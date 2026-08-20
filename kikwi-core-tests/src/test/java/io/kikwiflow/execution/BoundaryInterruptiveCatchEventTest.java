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
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT} (o "EVENT_CATCHER de borda"): um evento de borda
 * interruptivo anexado a um {@code EXTERNAL_TASK}/{@code EXECUTABLE_TASK} que, em vez de disparar por prazo
 * (timer), espera uma correlação externa via {@code KikwiflowEngine.correlateMessage(...)} — exatamente como
 * um EVENT_CATCHER STANDALONE, só que sua chegada cancela o nó pai e desvia o fluxo pelas arestas de saída do
 * próprio evento de borda, no mesmo padrão de um {@code BOUNDARY_INTERRUPTIVE_TIMER}.
 */
@DisplayName("Dado um EXTERNAL_TASK/EXECUTABLE_TASK com um boundary interruptivo de correlação (BOUNDARY_INTERRUPTIVE_CATCH_EVENT)")
class BoundaryInterruptiveCatchEventTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    private TestEngine testEngine;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
    }

    @Test
    @DisplayName("Correlacionar a chave de cancelamento interrompe o EXTERNAL_TASK pai e desvia para a saída do catch event")
    void correlatingTheCancelKeyInterruptsTheExternalTaskParent() {
        ProcessDefinition definition = testEngine.deploy("/processes/boundary-catch-event-external-task.json");

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-EXT-1")
                .onTenant("tenant-a")
                .execute();

        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "COLETAR_DADOS");

        ProcessInstance completed = testEngine.engine().correlateMessage("cancelar-task-15649234", Map.of(), IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        testEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "COLETAR_DADOS");
        testEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "AFTER_COLETA");
    }

    @Test
    @DisplayName("Correlacionar a mesma chave duas vezes lança TaskNotFoundException na segunda (idempotência)")
    void correlatingTheCancelKeyTwiceThrowsOnTheSecondCall() {
        ProcessDefinition definition = testEngine.deploy("/processes/boundary-catch-event-external-task.json");

        testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-EXT-2")
                .onTenant("tenant-a")
                .execute();

        testEngine.engine().correlateMessage("cancelar-task-15649234", Map.of(), IDENTITY);

        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("cancelar-task-15649234", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Correlacionar uma chave desconhecida lança TaskNotFoundException")
    void correlatingAnUnknownKeyThrows() {
        ProcessDefinition definition = testEngine.deploy("/processes/boundary-catch-event-external-task.json");

        testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-EXT-3")
                .onTenant("tenant-a")
                .execute();

        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("chave-que-nao-existe", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Completar o EXTERNAL_TASK pai normalmente remove o catch event de borda ainda não disparado")
    void completingTheParentNormallyCleansUpTheUnfiredCatchEvent() {
        ProcessDefinition definition = testEngine.deploy("/processes/boundary-catch-event-external-task.json");

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-EXT-4")
                .onTenant("tenant-a")
                .execute();

        List<ExternalTask> tasks = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        ExternalTask coletarDados = tasks.stream()
                .filter(t -> "COLETAR_DADOS".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow();

        ProcessInstance afterComplete = testEngine.engine().completeExternalTask(coletarDados.id(), Map.of(), IDENTITY);

        // O processo segue ativo (há uma EXTERNAL_TASK depois de COLETAR_DADOS) — só assim dá pra provar que a
        // limpeza do catch event de borda não é apenas efeito colateral do wipe em bloco de uma instância
        // concluída: se o catch event não fosse removido, esta correlação abaixo NÃO lançaria (bug real).
        assertEquals(ProcessInstanceStatus.ACTIVE, afterComplete.status());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "AFTER_COLETA");
        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("cancelar-task-15649234", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Deploy rejeita um catch event de borda interruptivo anexado a um EXECUTABLE_TASK")
    void deployRejectsAnInterruptiveCatchEventAttachedToAnExecutableTask() {
        // Até esta validação existir, este mesmo fixture era usado para provar que a correlação interrompia
        // PROCESSAR_DADOS (EXECUTABLE_TASK, commitBefore=true) normalmente. Isso foi deliberadamente proibido:
        // um EXECUTABLE_TASK executa seu handler de forma síncrona, na mesma thread que o adquiriu — não há
        // como interrompê-lo de fora a meio caminho sem risco de um efeito colateral real (ex.: chamada de
        // API) já ter acontecido antes da interrupção vencer a corrida de finalização, sem forma de desfazê-lo
        // depois. Ver docs/engine/19-guard-de-finalizacao-boundary-events.md.
        TestEngine executableEngine = SingletonsFactory.engine()
                .withTaskHandler("processDataTaskHandler", ctx -> {})
                .build();

        // TestEngine.deploy embrulha qualquer falha em RuntimeException — a causa real é
        // InvalidProcessDefinitionException, lançada por DeployValidator.
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                executableEngine.deploy("/processes/boundary-catch-event-executable-task.json"));
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("A mesma chave técnica (STATIC) em tenants diferentes é isolada em correlateMessage")
    void staticKeyIsIsolatedByTenant() {
        ProcessDefinition definition = testEngine.deploy("/processes/boundary-catch-event-external-task.json");
        IdentityContext tenantBIdentity = new IdentityContext("test-actor", "tenant-b");

        ProcessInstance instanceTenantA = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-TENANT-A")
                .onTenant("tenant-a")
                .execute();
        ProcessInstance instanceTenantB = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CATCH-TENANT-B")
                .onTenant("tenant-b")
                .execute();

        ProcessInstance completedTenantA = testEngine.engine().correlateMessage(
                "cancelar-task-15649234", Map.of(), IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, completedTenantA.status());
        testEngine.repository().assertThatProcessInstanceIsActive(instanceTenantB.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instanceTenantB.id(), "COLETAR_DADOS");

        ProcessInstance completedTenantB = testEngine.engine().correlateMessage(
                "cancelar-task-15649234", Map.of(), tenantBIdentity);
        assertEquals(ProcessInstanceStatus.COMPLETED, completedTenantB.status());

        // apenas para deixar explícito no teste que a instância do tenant A foi a primeira a concluir
        assertEquals("BK-CATCH-TENANT-A", instanceTenantA.businessKey());
    }

    /**
     * Cobre o guard de finalização documentado em {@code UnitOfWork.finalizingNodeId}: um mesmo
     * {@code EXTERNAL_TASK} com DOIS boundary events interruptivos anexados (um timer e um catch event) —
     * disparar qualquer um dos dois precisa cancelar o irmão remanescente, não só o pai. Antes desse guard,
     * o irmão que não disparou ficava órfão no banco (nunca era limpo).
     */
    @Nested
    @DisplayName("Quando o EXTERNAL_TASK pai tem dois boundary events interruptivos (timer + catch event)")
    class TwoInterruptiveSiblingsAttachedToTheSameParent {

        @Test
        @DisplayName("Disparar o timer cancela o catch event irmão que ainda não tinha disparado")
        void firingTheTimerSiblingAlsoCancelsTheUnfiredCatchEventSibling() {
            ProcessDefinition definition = testEngine.deploy("/processes/boundary-dual-timer-and-catch-event.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-DUAL-1")
                    .onTenant("tenant-a")
                    .execute();

            ExecutableTask timeoutTimer = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> "TIMEOUT_TIMER".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterFiring = testEngine.engine().executeFromTask(timeoutTimer);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterFiring.status());
            assertThrows(TaskNotFoundException.class, () ->
                            testEngine.engine().correlateMessage("cancelar-task-dual-1", Map.of(), IDENTITY),
                    "O catch event irmão deveria ter sido cancelado junto com o pai — não deve mais existir para correlacionar.");
        }

        @Test
        @DisplayName("Correlacionar o catch event cancela o timer irmão que ainda não tinha disparado")
        void correlatingTheCatchEventSiblingAlsoCancelsTheUnfiredTimerSibling() {
            ProcessDefinition definition = testEngine.deploy("/processes/boundary-dual-timer-and-catch-event.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-DUAL-2")
                    .onTenant("tenant-a")
                    .execute();

            ProcessInstance afterCorrelation = testEngine.engine().correlateMessage("cancelar-task-dual-1", Map.of(), IDENTITY);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterCorrelation.status());
            List<ExecutableTask> remaining = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            assertTrue(remaining.stream().noneMatch(t -> "TIMEOUT_TIMER".equals(t.taskDefinitionId())),
                    "O timer irmão deveria ter sido cancelado junto com o pai, não deixado órfão no banco.");
        }

        @Test
        @DisplayName("Completar o pai normalmente cancela os dois boundary events, não só um deles")
        void completingTheParentNormallyCleansUpBothUnfiredSiblings() {
            ProcessDefinition definition = testEngine.deploy("/processes/boundary-dual-timer-and-catch-event.json");

            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-DUAL-3")
                    .onTenant("tenant-a")
                    .execute();

            ExternalTask coletarDados = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> "COLETAR_DADOS".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterComplete = testEngine.engine().completeExternalTask(coletarDados.id(), Map.of(), IDENTITY);

            assertEquals(ProcessInstanceStatus.ACTIVE, afterComplete.status());
            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "AFTER_COLETA");
            assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id())
                            .stream().noneMatch(t -> "TIMEOUT_TIMER".equals(t.taskDefinitionId())),
                    "O timer de borda deveria ter sido removido junto com a conclusão normal do pai.");
            assertThrows(TaskNotFoundException.class, () ->
                            testEngine.engine().correlateMessage("cancelar-task-dual-1", Map.of(), IDENTITY),
                    "O catch event de borda deveria ter sido removido junto com a conclusão normal do pai.");
        }
    }

    /**
     * Cobre {@code ProcessExecutionManager.executeFlow}'s {@code guardSynchronousHandlers}: a saída de um
     * boundary event interruptivo (timer ou catch event) não pode rodar um {@code EXECUTABLE_TASK} síncrono
     * de verdade antes do guard de finalização em {@code commitWork} decidir quem venceu a corrida — ver
     * docs/engine/19-guard-de-finalizacao-boundary-events.md, seção "fluxo fantasma". A fixture usada
     * (`boundary-interruptive-guards-downstream-handler.json`) leva direto de cada boundary event a um
     * {@code EXECUTABLE_TASK} com {@code commitBefore: false} — sem o guard, o handler rodaria inline.
     */
    @Nested
    @DisplayName("Quando a saída de um boundary event interruptivo leva direto a um EXECUTABLE_TASK sem commitBefore")
    class InterruptiveBoundaryGuardsDownstreamHandler {

        private final AtomicInteger handlerInvocations = new AtomicInteger(0);

        @Test
        @DisplayName("Correlacionar o catch event não roda o handler inline — a tarefa fica PENDING até um ciclo separado")
        void catchEventDoesNotRunDownstreamHandlerInline() {
            TestEngine engine = SingletonsFactory.engine()
                    .withTaskHandler("realizarPagamentoTaskHandler", ctx -> handlerInvocations.incrementAndGet())
                    .build();
            ProcessDefinition definition = engine.deploy("/processes/boundary-interruptive-guards-downstream-handler.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GUARD-CATCH-1")
                    .onTenant("tenant-a")
                    .execute();

            ProcessInstance afterCorrelation = engine.engine().correlateMessage("cancelar-task-guard-1", Map.of(), IDENTITY);

            assertEquals(ProcessInstanceStatus.ACTIVE, afterCorrelation.status(),
                    "O processo não deveria concluir ainda — o handler de pagamento não rodou de verdade.");
            assertEquals(0, handlerInvocations.get(),
                    "O handler NÃO deveria ter rodado inline — a corrida de finalização já foi resolvida no banco, "
                            + "mas o handler só roda quando um ciclo separado pegar a tarefa PENDING.");

            ExecutableTask pagamento = engine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> "REALIZAR_PAGAMENTO_CATCH".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("REALIZAR_PAGAMENTO_CATCH deveria existir como ExecutableTask PENDING."));

            ProcessInstance afterHandlerRuns = engine.engine().executeFromTask(pagamento);

            assertEquals(1, handlerInvocations.get(), "Agora sim, o handler deveria ter rodado — num ciclo separado.");
            assertEquals(ProcessInstanceStatus.COMPLETED, afterHandlerRuns.status());
        }

        @Test
        @DisplayName("Disparar o timer não roda o handler inline — a tarefa fica PENDING até um ciclo separado")
        void timerDoesNotRunDownstreamHandlerInline() {
            TestEngine engine = SingletonsFactory.engine()
                    .withTaskHandler("realizarPagamentoTaskHandler", ctx -> handlerInvocations.incrementAndGet())
                    .build();
            ProcessDefinition definition = engine.deploy("/processes/boundary-interruptive-guards-downstream-handler.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GUARD-TIMER-1")
                    .onTenant("tenant-a")
                    .execute();

            ExecutableTask timeoutTimer = engine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> "TIMEOUT_TIMER".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterFiring = engine.engine().executeFromTask(timeoutTimer);

            assertEquals(ProcessInstanceStatus.ACTIVE, afterFiring.status(),
                    "O processo não deveria concluir ainda — o handler de pagamento não rodou de verdade.");
            assertEquals(0, handlerInvocations.get(), "O handler NÃO deveria ter rodado inline.");

            ExecutableTask pagamento = engine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> "REALIZAR_PAGAMENTO_TIMEOUT".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("REALIZAR_PAGAMENTO_TIMEOUT deveria existir como ExecutableTask PENDING."));

            ProcessInstance afterHandlerRuns = engine.engine().executeFromTask(pagamento);

            assertEquals(1, handlerInvocations.get(), "Agora sim, o handler deveria ter rodado — num ciclo separado.");
            assertEquals(ProcessInstanceStatus.COMPLETED, afterHandlerRuns.status());
        }
    }

    /**
     * Confirma que o cancelamento recursivo de filhos de {@code CALL_ACTIVITY_COORDINATOR} (disparado por
     * {@code BOUNDARY_INTERRUPTIVE_TIMER}, ver docs/engine/20-subprocessos-call-activity-especificacao.md, §5)
     * não contamina um {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT} correndo num ramo irmão da mesma instância — os
     * dois mecanismos compartilham o mesmo guard de finalização (`UnitOfWork.finalizingNodeId`) dentro de
     * {@code commitWork}, então esta é a prova direta de que um não pisa no outro. Fixture
     * (`call-activity-and-catch-event-independence.json`): {@code PARALLEL_GATEWAY} com dois ramos — um
     * {@code CALL_ACTIVITY_COORDINATOR} com boundary timeout, outro um {@code EXTERNAL_TASK} com boundary catch
     * event — sincronizando num {@code JOIN_GATEWAY} comum.
     */
    @Nested
    @DisplayName("Quando um CALL_ACTIVITY_COORDINATOR com boundary timeout roda em paralelo com um catch event de borda (ramos irmãos)")
    class IndependenceFromCallActivityCancellationCascade {

        private TestEngine engine;
        private final AtomicInteger childTaskRuns = new AtomicInteger(0);
        private final AtomicInteger afterJoinRuns = new AtomicInteger(0);

        @BeforeEach
        void setUp() {
            engine = SingletonsFactory.engine()
                    .withConfig(config -> config.setOutboxEventsEnabled(true))
                    .withTaskHandler("childTaskHandler", ctx -> childTaskRuns.incrementAndGet())
                    .withTaskHandler("afterJoinHandler", ctx -> afterJoinRuns.incrementAndGet())
                    .build();
            engine.deploy("/processes/call-activity-child-process.json");
        }

        private ExecutableTask findExecutableByDefinitionId(String processInstanceId, String taskDefinitionId) {
            return engine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                    .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Esperava uma ExecutableTask para '" + taskDefinitionId + "'."));
        }

        @Test
        @DisplayName("Disparar o timeout da call activity cancela só o próprio ramo — o catch event irmão continua 100% funcional")
        void firingCallActivityTimeoutDoesNotAffectSiblingCatchEventBranch() {
            ProcessDefinition definition = engine.deploy("/processes/call-activity-and-catch-event-independence.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-INDEP-1")
                    .onTenant("tenant-a")
                    .execute();

            // Inicia o filho da call activity antes do timeout, pra provar o cancelamento recursivo de verdade
            // (não só a limpeza de uma iniciadora ainda pendente).
            ExecutableTask starter = engine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER)
                    .findFirst()
                    .orElseThrow();
            ProcessInstance childInstance = engine.engine().executeFromTask(starter);
            engine.repository().assertThatProcessInstanceIsActive(childInstance.id());
            engine.repository().assertHasActiveExternalTaskOn(instance.id(), "COLETAR_DADOS");

            ExecutableTask timeoutTimer = findExecutableByDefinitionId(instance.id(), "CALL_TIMEOUT");
            ProcessInstance afterTimeout = engine.engine().executeFromTask(timeoutTimer);

            // O processo NÃO deveria concluir ainda — falta o ramo do COLETAR_DADOS no join.
            assertEquals(ProcessInstanceStatus.ACTIVE, afterTimeout.status());

            // O ramo da call activity foi cancelado de verdade (o filho já iniciado não ficou órfão rodando)...
            engine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(childInstance.id());
            assertTrue(engine.repository().findExecutableTasksByProcessInstanceId(instance.id())
                    .stream().noneMatch(t -> "CALL_CHILD".equals(t.taskDefinitionId())));

            // ...mas o ramo irmão (catch event) está 100% intacto: a tarefa externa e a chave de correlação
            // continuam vivas e funcionais, sem nenhuma interferência da cascata de cancelamento.
            engine.repository().assertHasActiveExternalTaskOn(instance.id(), "COLETAR_DADOS");
            ProcessInstance afterCorrelation = engine.engine().correlateMessage(
                    "cancelar-coleta-independence", Map.of(), IDENTITY);
            assertEquals(ProcessInstanceStatus.ACTIVE, afterCorrelation.status(),
                    "Os dois ramos concluíram, mas o JOIN_GATEWAY liberado só continua com um executeFromTask explícito.");

            ExecutableTask joinReleased = findExecutableByDefinitionId(instance.id(), "JOIN_SYNC");
            ProcessInstance completed = engine.engine().executeFromTask(joinReleased);

            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
            assertEquals(1, afterJoinRuns.get(), "O join só deveria liberar depois dos DOIS ramos concluírem.");
        }

        @Test
        @DisplayName("Correlacionar o catch event não afeta o ramo irmão da call activity — o filho já iniciado segue ativo até o timeout")
        void correlatingCatchEventDoesNotAffectSiblingCallActivityBranch() {
            ProcessDefinition definition = engine.deploy("/processes/call-activity-and-catch-event-independence.json");

            ProcessInstance instance = engine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-INDEP-2")
                    .onTenant("tenant-a")
                    .execute();

            ExecutableTask starter = engine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                    .filter(t -> t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER)
                    .findFirst()
                    .orElseThrow();
            ProcessInstance childInstance = engine.engine().executeFromTask(starter);

            ProcessInstance afterCorrelation = engine.engine().correlateMessage(
                    "cancelar-coleta-independence", Map.of(), IDENTITY);

            // Processo ainda ativo — falta o ramo da call activity no join.
            assertEquals(ProcessInstanceStatus.ACTIVE, afterCorrelation.status());

            // O ramo da call activity e o filho já iniciado seguem 100% intactos — a correlação do catch event
            // irmão não tocou em nada aqui.
            engine.repository().assertThatProcessInstanceIsActive(childInstance.id());
            assertTrue(engine.repository().findExecutableTasksByProcessInstanceId(instance.id())
                    .stream().anyMatch(t -> "CALL_CHILD".equals(t.taskDefinitionId())));

            ExecutableTask timeoutTimer = findExecutableByDefinitionId(instance.id(), "CALL_TIMEOUT");
            ProcessInstance afterTimeout = engine.engine().executeFromTask(timeoutTimer);
            assertEquals(ProcessInstanceStatus.ACTIVE, afterTimeout.status(),
                    "Os dois ramos concluíram, mas o JOIN_GATEWAY liberado só continua com um executeFromTask explícito.");

            engine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(childInstance.id());

            ExecutableTask joinReleased = findExecutableByDefinitionId(instance.id(), "JOIN_SYNC");
            ProcessInstance completed = engine.engine().executeFromTask(joinReleased);

            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
            assertEquals(1, afterJoinRuns.get());
        }
    }
}
