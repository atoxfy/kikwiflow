/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kikwiflow.execution;

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.exception.ProcessInstanceNotFoundException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OrphanedChildCompletion;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code CALL_ACTIVITY_COORDINATOR} (docs/engine/20-subprocessos-call-activity-especificacao.md):
 * alcançar o nó gera, numa única transação sem efeito colateral, 1 {@code ExecutableTask} coordenadora
 * ({@code AWAITING_BRANCHES}) + N {@code ExecutableTask} iniciadoras ({@code CALL_ACTIVITY_STARTER},
 * {@code PENDING}) — mesmo padrão "um nó → N tarefas-filha" do modo GROUP de {@code EVENT_CATCHER}. Cada
 * iniciadora, ao ser retomada, chama {@code KikwiflowEngine.startProcess()} para {@code calledElement} numa
 * transação própria e se auto-apaga; o retorno do filho libera a coordenadora via o mesmo mecanismo de
 * {@code BranchPullIntention}/{@code $pull} que {@code PARALLEL_GATEWAY}/{@code JOIN_GATEWAY} já usam, só que
 * atravessando a fronteira de instância.
 */
@DisplayName("Dado um processo com um CALL_ACTIVITY_COORDINATOR chamando outro processo")
class CallActivityCoordinatorTest {

    private TestEngine testEngine;
    private ProcessDefinition childDefinition;
    private final AtomicInteger childTaskRuns = new AtomicInteger(0);
    private final AtomicBoolean afterCallRan = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withConfig(config -> config.setOutboxEventsEnabled(true))
                .withTaskHandler("childTaskHandler", ctx -> childTaskRuns.incrementAndGet())
                .withTaskHandler("afterCallHandler", ctx -> afterCallRan.set(true))
                .build();

        childDefinition = testEngine.deploy("/processes/call-activity-child-process.json");
    }

    private ExecutableTask findTaskByDefinitionId(String processInstanceId, String taskDefinitionId) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Esperava uma ExecutableTask para '" + taskDefinitionId + "'."));
    }

    private List<ExecutableTask> findTasksByType(String processInstanceId, ExecutableTaskType type) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> t.type() == type)
                .toList();
    }

    /**
     * O coordenador e cada iniciadora compartilham o mesmo {@code taskDefinitionId} (o id do nó
     * CALL_ACTIVITY_COORDINATOR no processo) — só o {@code type} os distingue.
     */
    private ExecutableTask findCoordinatorTask(String processInstanceId, String taskDefinitionId) {
        List<ExecutableTask> coordinators = findTasksByType(processInstanceId, ExecutableTaskType.CALL_ACTIVITY_COORDINATOR).stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .toList();
        assertEquals(1, coordinators.size(), "Esperava exatamente uma coordenadora para '" + taskDefinitionId + "'.");
        return coordinators.get(0);
    }

    @Nested
    @DisplayName("Sem collectionVariable (um único filho)")
    class SingleChild {

        @Test
        @DisplayName("Alcançar o coordenador persiste 1 iniciadora PENDING + 1 coordenadora AWAITING_BRANCHES, sem efeito colateral")
        void reachingCoordinatorPersistsStarterAndCoordinatorWithoutSideEffects() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-single-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-1")
                    .execute();

            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

            List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(1, starters.size());
            ExecutableTask starter = starters.get(0);
            assertEquals(ExecutableTaskStatus.PENDING, starter.status());
            assertEquals(0, starter.loopIndex());

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskType.CALL_ACTIVITY_COORDINATOR, coordinator.type());
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, coordinator.status());
            assertEquals(1, coordinator.pendingBranchIds().size());
            assertEquals(coordinator.id() + ":0", starter.branchId(),
                    "branchId deveria ser determinístico: coordinatorTaskId + ':' + loopIndex.");
            assertEquals(coordinator.id(), starter.joinTaskId());

            assertEquals(0, childTaskRuns.get(), "Nenhum efeito colateral real deveria ter acontecido ainda.");
        }

        @Test
        @DisplayName("Executar a iniciadora spawna o filho e retorna sua ProcessInstance, sem gerar continuação no pai")
        void executingStarterSpawnsChildAndDeletesItself() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-single-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-2")
                    .onTenant("tenant-x")
                    .execute();

            ExecutableTask starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ExecutableTask coordinatorBefore = findCoordinatorTask(instance.id(), "CALL_CHILD");

            ProcessInstance childInstance = testEngine.engine().executeFromTask(starter);

            assertEquals(childDefinition.id(), childInstance.processDefinitionId());
            assertEquals(instance.id(), childInstance.parentInstanceId());
            assertEquals(coordinatorBefore.id(), childInstance.callerTaskId());
            assertEquals(starter.branchId(), childInstance.callerBranchId());
            assertEquals("tenant-x", childInstance.tenantId());

            assertTrue(findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).isEmpty(),
                    "A iniciadora deveria ter se auto-apagado.");

            ExecutableTask coordinatorAfter = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, coordinatorAfter.status(),
                    "A coordenadora continua esperando — só o retorno do filho a libera.");

            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        }

        @Test
        @DisplayName("Quando o filho conclui, a coordenadora libera para PENDING e, ao ser retomada, o pai segue seu outgoing")
        void childCompletionReleasesCoordinatorAndParentContinues() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-single-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-3")
                    .execute();

            ExecutableTask starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance childInstance = testEngine.engine().executeFromTask(starter);

            ExecutableTask childTask = findTaskByDefinitionId(childInstance.id(), "CHILD_TASK");
            ProcessInstance completedChild = testEngine.engine().executeFromTask(childTask);
            assertEquals(ProcessInstanceStatus.COMPLETED, completedChild.status());
            assertEquals(1, childTaskRuns.get());

            ExecutableTask coordinatorAfterChild = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinatorAfterChild.status());
            assertTrue(coordinatorAfterChild.pendingBranchIds().isEmpty());

            ProcessInstance completedParent = testEngine.engine().executeFromTask(coordinatorAfterChild);
            assertTrue(afterCallRan.get());
            assertEquals(ProcessInstanceStatus.COMPLETED, completedParent.status());
        }
    }

    @Nested
    @DisplayName("Com collectionVariable (N filhos em paralelo)")
    class MultiChild {

        @Test
        @DisplayName("N elementos geram N iniciadoras com branchId/loopIndex/loopElement distintos")
        void collectionGeneratesOneStarterPerElement() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-multi-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-MULTI-1")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(3, starters.size());

            Set<String> branchIds = starters.stream().map(ExecutableTask::branchId).collect(Collectors.toSet());
            assertEquals(3, branchIds.size(), "Cada iniciadora deveria ter um branchId distinto.");

            List<Integer> loopIndexes = starters.stream().map(ExecutableTask::loopIndex).sorted().toList();
            assertEquals(List.of(0, 1, 2), loopIndexes);

            List<Object> loopElements = starters.stream()
                    .sorted(Comparator.comparing(ExecutableTask::loopIndex))
                    .map(t -> t.loopElement().value())
                    .toList();
            assertEquals(List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"), loopElements);

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(3, coordinator.pendingBranchIds().size());
        }

        @Test
        @DisplayName("A coordenadora só libera após TODOS os filhos concluírem — completar N-1 mantém AWAITING_BRANCHES")
        void coordinatorReleasesOnlyAfterAllChildrenComplete() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-multi-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-MULTI-2")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf"))))
                    .execute();

            List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(2, starters.size());

            ProcessInstance child1 = testEngine.engine().executeFromTask(starters.get(0));
            ProcessInstance child2 = testEngine.engine().executeFromTask(starters.get(1));

            testEngine.engine().executeFromTask(findTaskByDefinitionId(child1.id(), "CHILD_TASK"));

            ExecutableTask coordinatorAfterOne = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, coordinatorAfterOne.status());
            assertEquals(1, coordinatorAfterOne.pendingBranchIds().size());

            testEngine.engine().executeFromTask(findTaskByDefinitionId(child2.id(), "CHILD_TASK"));

            ExecutableTask coordinatorAfterBoth = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinatorAfterBoth.status());

            ProcessInstance completedParent = testEngine.engine().executeFromTask(coordinatorAfterBoth);
            assertEquals(ProcessInstanceStatus.COMPLETED, completedParent.status());
            assertEquals(2, childTaskRuns.get());
        }

        @Test
        @DisplayName("elementVariable propaga o elemento da vez + todas as variáveis do pai para o filho")
        void variablePropagationIncludesParentVariablesAndElement() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-multi-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-MULTI-3")
                    .withVariables(Map.of(
                            "documents", new ProcessVariable("documents", List.of("doc-1.pdf")),
                            "requestId", new ProcessVariable("requestId", "REQ-99")))
                    .execute();

            ExecutableTask starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance childInstance = testEngine.engine().executeFromTask(starter);

            assertEquals("doc-1.pdf", childInstance.variables().get("document").value());
            assertEquals("REQ-99", childInstance.variables().get("requestId").value());
        }

        @Test
        @DisplayName("Coleção vazia: coordenadora nasce direto PENDING, sem nenhuma iniciadora")
        void emptyCollectionSkipsTheWaitEntirely() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-multi-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-EMPTY-1")
                    .withVariables(Map.of("documents", new ProcessVariable("documents", List.of())))
                    .execute();

            assertTrue(findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).isEmpty());

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinator.status());
            assertTrue(coordinator.pendingBranchIds().isEmpty());

            ProcessInstance completed = testEngine.engine().executeFromTask(coordinator);
            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }
    }

    @Nested
    @DisplayName("Boundary event de timeout na coordenadora")
    class BoundaryTimeout {

        @Test
        @DisplayName("Disparar o timer apaga a coordenadora, qualquer iniciadora ainda pendente E cancela um filho já iniciado")
        void firingTheTimeoutCancelsCoordinatorPendingStartersAndAlreadyStartedChildren() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-timeout-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-TIMEOUT-1")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf"))))
                    .execute();

            List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(2, starters.size());

            // Só inicia o primeiro filho — o segundo fica com sua iniciadora ainda PENDING quando o timeout disparar.
            ProcessInstance startedChild = testEngine.engine().executeFromTask(starters.get(0));

            ExecutableTask timeoutTimer = findTaskByDefinitionId(instance.id(), "CALL_TIMEOUT");
            ProcessInstance afterTimeout = testEngine.engine().executeFromTask(timeoutTimer);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());

            List<ExecutableTask> remainingParentTasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            assertTrue(remainingParentTasks.stream().noneMatch(t -> "CALL_CHILD".equals(t.taskDefinitionId())),
                    "A coordenadora deveria ter sido apagada pelo timeout.");
            assertTrue(remainingParentTasks.stream().noneMatch(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER),
                    "A iniciadora do segundo filho, ainda pendente, deveria ter sido apagada junto.");

            // Comportamento novo: o filho já iniciado não fica mais rodando sozinho — o boundary event
            // interruptivo cancela de verdade a instância filha e suas tasks, não só a espera do pai.
            testEngine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(startedChild.id());
            assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(startedChild.id()).isEmpty(),
                    "As tasks do filho cancelado (CHILD_TASK) também deveriam ter sido apagadas.");

            List<OutboxEventEntity> childEvents = testEngine.repository().findEventHistoryByProcessInstanceId(startedChild.id());
            OutboxEventEntity cancelledEvent = childEvents.stream()
                    .filter(e -> e.getPayload() instanceof ProcessInstanceFinished finished && finished.getStatus() == ProcessInstanceStatus.CANCELLED)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Esperava um evento PROCESS_INSTANCE_FINISHED com status CANCELLED para o filho cancelado."));

            ProcessInstanceFinished payload = (ProcessInstanceFinished) cancelledEvent.getPayload();
            assertEquals(startedChild.id(), payload.getId());
            assertEquals(instance.id(), payload.getParentInstanceId());
        }

        @Test
        @DisplayName("Tentar completar um filho depois do timeout falha com ProcessInstanceNotFoundException — ele já foi cancelado, não deixado rodando")
        void completingChildAfterCoordinatorTimeoutFailsBecauseChildWasCancelled() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-timeout-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-ORPHAN-1")
                    .withVariables(Map.of("documents", new ProcessVariable("documents", List.of("doc-1.pdf"))))
                    .execute();

            ExecutableTask starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance childInstance = testEngine.engine().executeFromTask(starter);

            ExecutableTask timeoutTimer = findTaskByDefinitionId(instance.id(), "CALL_TIMEOUT");
            testEngine.engine().executeFromTask(timeoutTimer); // agora cancela o filho já iniciado junto com a coordenadora

            // A ExecutableTask CHILD_TASK do filho também já foi apagada pela cascata — simula quem ainda
            // segurava a referência (ex.: um worker que já tinha adquirido a task antes do cancelamento)
            // tentando completá-la de qualquer forma.
            ExecutableTask staleChildTask = ExecutableTask.builder()
                    .id("stale-task-id")
                    .processDefinitionId(childInstance.processDefinitionId())
                    .taskDefinitionId("CHILD_TASK")
                    .processInstanceId(childInstance.id())
                    .type(ExecutableTaskType.STANDARD)
                    .build();

            assertThrows(ProcessInstanceNotFoundException.class,
                    () -> testEngine.engine().executeFromTask(staleChildTask),
                    "A instância filha já foi cancelada junto com o timeout — completá-la depois deveria falhar alto, não silencioso.");
        }

        @Test
        @DisplayName("Timeout cancela recursivamente netos gerados por uma call activity aninhada dentro do filho")
        void firingTheTimeoutRecursivelyCancelsGrandchildrenFromNestedCallActivity() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-nested-timeout-parent.json");
            testEngine.deploy("/processes/call-activity-nested-middle-process.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-NESTED-1")
                    .execute();

            ExecutableTask starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance middleInstance = testEngine.engine().executeFromTask(starter);
            testEngine.repository().assertThatProcessInstanceIsActive(middleInstance.id());

            // O filho (middleInstance) já nasce com sua PRÓPRIA coordenadora + iniciadora (CALL_GRANDCHILD),
            // geradas sincronamente durante o próprio startProcess() (CallActivityDefinition é sempre
            // commitBefore) — não é o pai que gera o neto, é a call activity de dentro do filho.
            ExecutableTask grandchildStarter = findTasksByType(middleInstance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance grandchildInstance = testEngine.engine().executeFromTask(grandchildStarter);
            testEngine.repository().assertThatProcessInstanceIsActive(grandchildInstance.id());

            // Confirma que o neto está mesmo esperando (CHILD_TASK pendente) antes de disparar o timeout no avô.
            findTaskByDefinitionId(grandchildInstance.id(), "CHILD_TASK");

            ExecutableTask timeoutTimer = findTaskByDefinitionId(instance.id(), "CALL_TIMEOUT");
            ProcessInstance afterTimeout = testEngine.engine().executeFromTask(timeoutTimer);
            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());

            // Cascata recursiva: filho E neto somem, junto com suas tasks — não só o filho direto do avô.
            testEngine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(middleInstance.id());
            testEngine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(grandchildInstance.id());
            assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(middleInstance.id()).isEmpty());
            assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(grandchildInstance.id()).isEmpty());

            // Cada um dos dois (filho e neto) recebe seu próprio evento CANCELLED.
            for (String cancelledId : List.of(middleInstance.id(), grandchildInstance.id())) {
                List<OutboxEventEntity> events = testEngine.repository().findEventHistoryByProcessInstanceId(cancelledId);
                assertTrue(events.stream().anyMatch(e -> e.getPayload() instanceof ProcessInstanceFinished finished
                                && finished.getStatus() == ProcessInstanceStatus.CANCELLED),
                        "Esperava um evento PROCESS_INSTANCE_FINISHED com status CANCELLED para " + cancelledId);
            }
        }
    }

    @Nested
    @DisplayName("Falha na iniciadora")
    class StarterFailure {

        @Test
        @DisplayName("calledElement não resolvido isola retry/incidente à própria iniciadora — a coordenadora não é afetada")
        void unresolvableCalledElementIsolatesFailureToTheStarterTask() {
            TestEngine isolatedEngine = SingletonsFactory.engine()
                    .withConfig(config -> {
                        config.setOutboxEventsEnabled(true);
                        config.setDefaultMaxRetries(0);
                    })
                    .withTaskHandler("afterCallHandler", ctx -> {})
                    .build();
            // Deliberadamente NÃO deploya o processo filho — calledElement não resolve.
            ProcessDefinition parent = isolatedEngine.deploy("/processes/call-activity-single-child-parent.json");

            ProcessInstance instance = isolatedEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-FAIL-1")
                    .execute();

            ExecutableTask starter = isolatedEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER)
                    .findFirst()
                    .orElseThrow();

            isolatedEngine.engine().executeFromTask(starter);

            List<Incident> incidents = isolatedEngine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(1, incidents.size());
            assertEquals(starter.id(), incidents.get(0).executionId(),
                    "O incidente deveria estar isolado à própria iniciadora (branch), não a outro nó.");

            ExecutableTask coordinator = isolatedEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_COORDINATOR)
                    .findFirst()
                    .orElseThrow();
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, coordinator.status(),
                    "A coordenadora não deveria ser afetada pela falha isolada da iniciadora.");
        }
    }

    @Nested
    @DisplayName("iterationMode SEQUENTIAL (um filho por vez)")
    class SequentialIteration {

        @Test
        @DisplayName("Com N elementos, o fan-out inicial cria só 1 iniciadora — não N — e a coordenadora tem 1 branch pendente")
        void initialFanOutCreatesOnlyTheFirstStarter() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-sequential-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-1")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(1, starters.size(), "Modo SEQUENTIAL não deveria criar as 3 iniciadoras de uma vez.");
            assertEquals(0, starters.get(0).loopIndex());
            assertEquals("doc-1.pdf", starters.get(0).loopElement().value());

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(1, coordinator.pendingBranchIds().size());
            assertEquals(2, coordinator.pendingLoopElements().size(),
                    "Os elementos 2 e 3 ainda não iniciados deveriam estar em pendingLoopElements.");
        }

        @Test
        @DisplayName("Completar o filho do índice 0 libera a coordenadora; retomá-la cria só a iniciadora do índice 1")
        void completingFirstChildAdvancesToSecondStarterOnly() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-sequential-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-2")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            ExecutableTask starter0 = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance child0 = testEngine.engine().executeFromTask(starter0);
            testEngine.engine().executeFromTask(findTaskByDefinitionId(child0.id(), "CHILD_TASK"));

            ExecutableTask coordinatorPending = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinatorPending.status());

            testEngine.engine().executeFromTask(coordinatorPending);

            List<ExecutableTask> startersAfter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);
            assertEquals(1, startersAfter.size(), "Nunca deveria haver duas iniciadoras pendentes ao mesmo tempo em modo SEQUENTIAL.");
            assertEquals(1, startersAfter.get(0).loopIndex());
            assertEquals("doc-2.pdf", startersAfter.get(0).loopElement().value());

            ExecutableTask coordinatorAfter = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, coordinatorAfter.status());
            assertEquals(1, coordinatorAfter.pendingBranchIds().size());
            assertEquals(1, coordinatorAfter.pendingLoopElements().size(), "Só o elemento 3 deveria restar.");
        }

        @Test
        @DisplayName("Após o último filho concluir, a coordenadora segue para AFTER_CALL e o processo pai completa")
        void allChildrenCompletingInOrderFinishesTheParent() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-sequential-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-3")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            for (int expectedIndex = 0; expectedIndex < 3; expectedIndex++) {
                ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
                List<ExecutableTask> starters = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER);

                ExecutableTask starter;
                if (!starters.isEmpty()) {
                    starter = starters.get(0);
                } else {
                    testEngine.engine().executeFromTask(coordinator);
                    starter = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
                }

                assertEquals(expectedIndex, starter.loopIndex());
                ProcessInstance child = testEngine.engine().executeFromTask(starter);
                assertEquals("BK-CA-SEQ-3#" + expectedIndex, child.businessKey(),
                        "Sufixo de businessKey deveria seguir o índice do elemento, igual ao modo paralelo.");
                testEngine.engine().executeFromTask(findTaskByDefinitionId(child.id(), "CHILD_TASK"));
            }

            ExecutableTask coordinatorFinal = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinatorFinal.status());
            assertEquals(3, childTaskRuns.get(), "Os 3 filhos já deveriam ter rodado o handler antes da coordenadora liberar.");

            ProcessInstance completedParent = testEngine.engine().executeFromTask(coordinatorFinal);
            assertTrue(afterCallRan.get());
            assertEquals(ProcessInstanceStatus.COMPLETED, completedParent.status());
            assertEquals(3, childTaskRuns.get());
        }

        @Test
        @DisplayName("Coleção vazia em modo SEQUENTIAL: mesmo comportamento do modo PARALLEL — coordenadora nasce direto PENDING")
        void emptyCollectionBehavesLikeParallel() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-sequential-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-EMPTY")
                    .withVariables(Map.of("documents", new ProcessVariable("documents", List.of())))
                    .execute();

            assertTrue(findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).isEmpty());

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(ExecutableTaskStatus.PENDING, coordinator.status());
            assertTrue(coordinator.pendingBranchIds().isEmpty());

            ProcessInstance completed = testEngine.engine().executeFromTask(coordinator);
            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }

        @Test
        @DisplayName("Omitir iterationMode no JSON continua se comportando como PARALLEL (compatibilidade retroativa)")
        void omittingIterationModeStaysParallel() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-multi-child-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-COMPAT")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            assertEquals(3, findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).size(),
                    "Sem iterationMode declarado, as 3 iniciadoras continuam sendo criadas de uma vez (PARALLEL).");

            ExecutableTask coordinator = findCoordinatorTask(instance.id(), "CALL_CHILD");
            assertEquals(3, coordinator.pendingBranchIds().size());
            assertTrue(coordinator.pendingLoopElements() == null || coordinator.pendingLoopElements().isEmpty());
        }

        @Test
        @DisplayName("Timeout no meio da sequência apaga a coordenadora + a iniciadora corrente, cancela o filho em voo; elementos restantes nunca disparam")
        void boundaryTimeoutMidSequenceDiscardsRemainingElements() {
            ProcessDefinition parent = testEngine.deploy("/processes/call-activity-sequential-timeout-parent.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(parent.key())
                    .withBusinessKey("BK-CA-SEQ-TIMEOUT")
                    .withVariables(Map.of("documents", new ProcessVariable("documents",
                            List.of("doc-1.pdf", "doc-2.pdf", "doc-3.pdf"))))
                    .execute();

            // Só inicia o primeiro filho — elementos 2 e 3 ainda estão em pendingLoopElements quando o timeout dispara.
            ExecutableTask starter0 = findTasksByType(instance.id(), ExecutableTaskType.CALL_ACTIVITY_STARTER).get(0);
            ProcessInstance startedChild = testEngine.engine().executeFromTask(starter0);

            ExecutableTask timeoutTimer = findTaskByDefinitionId(instance.id(), "CALL_TIMEOUT");
            ProcessInstance afterTimeout = testEngine.engine().executeFromTask(timeoutTimer);
            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());

            List<ExecutableTask> remainingParentTasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            assertTrue(remainingParentTasks.stream().noneMatch(t -> "CALL_CHILD".equals(t.taskDefinitionId())),
                    "A coordenadora deveria ter sido apagada pelo timeout.");
            assertTrue(remainingParentTasks.stream().noneMatch(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER),
                    "Nenhuma iniciadora nova deveria ter sido criada para os elementos 2/3 — a coordenadora que os guardava se foi.");

            // Comportamento novo: o filho em voo (elemento 0, já iniciado) também é cancelado — não fica
            // órfão rodando sozinho, mesmo em modo SEQUENTIAL.
            testEngine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(startedChild.id());
            assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(startedChild.id()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Regras de validação em deploy-time")
    class DeployValidation {

        @Test
        @DisplayName("elementVariable sem collectionVariable é rejeitado")
        void elementVariableWithoutCollectionVariableIsRejected() {
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                    testEngine.deploy("/processes/call-activity-element-variable-without-collection.json"));
            assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
        }

        @Test
        @DisplayName("calledElement em branco é rejeitado")
        void blankCalledElementIsRejected() {
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                    testEngine.deploy("/processes/call-activity-blank-called-element.json"));
            assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
        }

        @Test
        @DisplayName("Um BOUNDARY_INTERRUPTIVE_CATCH_EVENT anexado ao coordenador agora é rejeitado em deploy-time, não só em runtime")
        void interruptiveCatchEventBoundaryIsRejectedAtDeployTime() {
            // Antes de DeployValidator.validateBoundaryEvents cobrir CALL_ACTIVITY_COORDINATOR, essa mesma
            // combinação só falhava em runtime (NotImplementedException em
            // ContinuationService.generateBoundaryEvents), quando o coordenador era de fato alcançado — não no
            // deploy. Só os dois tipos de timer são suportados aqui (ver
            // docs/engine/20-subprocessos-call-activity-especificacao.md, §5).
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                    testEngine.deploy("/processes/call-activity-invalid-boundary-catch-event.json"));
            assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
        }
    }
}
