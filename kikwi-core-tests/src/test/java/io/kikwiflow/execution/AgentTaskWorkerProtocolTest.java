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

import io.kikwiflow.exception.ExternalTaskLockException;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import io.kikwiflow.model.event.AgentActivityRecorded;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.EventCorrelated;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.AgentActivityReport;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.variable.VariableValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AGENT_TASK} e o protocolo de worker com lease (docs/engine/31, E1–E4): a missão nasce como
 * {@code ExternalTask} do tipo {@code AGENT} com tópico, só o dono do lease a trabalha, o contrato de saída é
 * validado no complete (inclusive {@code FILE}), falha técnica vira retry/incidente, erro de negócio segue pelo
 * error handler e cada passo deixa um evento de auditoria.
 */
@DisplayName("Dado um processo com uma missão de agente (AGENT_TASK)")
class AgentTaskWorkerProtocolTest {

    private static final IdentityContext WORKER_IDENTITY = IdentityContext.system();
    private static final String TOPIC = "kikwi.agents";
    private static final Duration LEASE = Duration.ofMinutes(5);

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withConfig(config -> config.setOutboxEventsEnabled(true))
                .build();
        definition = testEngine.deploy("/processes/agent-task.json");
    }

    private static Map<String, ProcessVariable> vars(Object... keyValues) {
        Map<String, ProcessVariable> variables = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            variables.put(key, new ProcessVariable(key, keyValues[i + 1]));
        }
        return variables;
    }

    private static Map<String, Object> photo(String name, String mimeType) {
        return Map.of("uri", "dms://" + name + "/1.0", "name", name, "mimeType", mimeType, "size", 1024);
    }

    private ProcessInstance start(String businessKey) {
        return testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey(businessKey)
                .withVariables(vars("conversationId", "conv-" + businessKey, "leadNome", "Ana"))
                .execute();
    }

    private LockedExternalTask fetchOne(String workerId) {
        List<LockedExternalTask> locked = testEngine.engine().fetchAndLock(workerId, List.of(TOPIC), 1, LEASE, WORKER_IDENTITY);
        assertEquals(1, locked.size(), "Deveria haver exatamente uma missão adquirível.");
        return locked.get(0);
    }

    private List<OutboxEventEntity> events(String processInstanceId, CriticalEventType type) {
        return testEngine.repository().findEventHistoryByProcessInstanceId(processInstanceId).stream()
                .filter(event -> type.name().equals(event.getEvent()))
                .toList();
    }

    private boolean reachedNode(String processInstanceId, String nodeId) {
        return events(processInstanceId, CriticalEventType.FLOW_NODE_FINISHED).stream()
                .map(event -> (FlowNodeFinished) event.getPayload())
                .anyMatch(node -> nodeId.equals(node.getFlowNodeDefinitionId()));
    }

    private ExternalTask mission(String processInstanceId) {
        return testEngine.repository().findExternalTasksByProcessInstanceId(processInstanceId).stream()
                .filter(task -> task.type() == ExternalTaskType.AGENT)
                .findFirst()
                .orElse(null);
    }

    @Nested
    @DisplayName("Quando a instância chega na missão")
    class WhenTheMissionIsCreated {

        @Test
        @DisplayName("então nasce uma ExternalTask AGENT com o tópico do nó, sem lease")
        void createsAnAgentExternalTask() {
            ProcessInstance instance = start("BK-AG-1");

            ExternalTask task = mission(instance.id());
            assertNotNull(task);
            assertEquals(TOPIC, task.topicName());
            assertNull(task.lockOwner());
        }

        @Test
        @DisplayName("e um worker a adquire, então recebe a tarefa travada, só as variáveis de inputs, o nó e o contrato")
        void fetchAndLockDeliversTheMission() {
            ProcessInstance instance = start("BK-AG-2");

            LockedExternalTask locked = fetchOne("worker-1");

            assertEquals("worker-1", locked.task().lockOwner());
            assertTrue(locked.task().lockExpiresAt().isAfter(Instant.now()));
            assertEquals("BK-AG-2", locked.businessKey());
            assertEquals(List.of("conversationId"), List.copyOf(locked.variables().keySet()),
                    "Com inputs declarados, o worker recebe só eles.");
            AgentTaskDefinition node = assertInstanceOf(AgentTaskDefinition.class, locked.node());
            assertEquals("pre-vendas", node.agentRef().key());
            assertEquals(List.of("conversationId", "resultado", "fotosVeiculo"),
                    locked.declarations().stream().map(d -> d.key()).toList());
            assertEquals(1, events(instance.id(), CriticalEventType.EXTERNAL_TASK_LOCKED).size());
        }

        @Test
        @DisplayName("e outro worker tenta adquirir durante o lease, então não recebe nada; vencido o lease, recebe")
        void leaseIsExclusiveUntilItExpires() throws InterruptedException {
            start("BK-AG-3");
            testEngine.engine().fetchAndLock("worker-1", List.of(TOPIC), 1, Duration.ofMillis(30), WORKER_IDENTITY);

            assertTrue(testEngine.engine().fetchAndLock("worker-2", List.of(TOPIC), 1, LEASE, WORKER_IDENTITY).isEmpty());

            Thread.sleep(60);
            LockedExternalTask retaken = fetchOne("worker-2");
            assertEquals("worker-2", retaken.task().lockOwner());
        }

        @Test
        @DisplayName("e um worker de outro tópico pede tarefas, então não recebe a missão")
        void otherTopicsDoNotSeeTheMission() {
            start("BK-AG-4");

            assertTrue(testEngine.engine().fetchAndLock("worker-1", List.of("outro.topico"), 5, LEASE, WORKER_IDENTITY).isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando o worker escolhe a missão pelo id (ex.: de uma lista de missões disponíveis)")
    class WhenTheWorkerLocksById {

        @Test
        @DisplayName("então a lista mostra a missão sem travar, e o lock por id entrega a mesma missão do fetchAndLock")
        void listsAndLocksTheChosenMission() {
            ProcessInstance instance = start("BK-AG-ID-1");
            ExternalTask task = mission(instance.id());

            List<ExternalTask> available = testEngine.repository().createExternalTaskQuery()
                    .topicName(TOPIC).type(ExternalTaskType.AGENT).fetchableAt(Instant.now()).list();
            assertEquals(List.of(task.id()), available.stream().map(ExternalTask::id).toList());
            assertNull(mission(instance.id()).lockOwner(), "Listar não trava.");

            LockedExternalTask locked = testEngine.engine().lockExternalTask(task.id(), "worker-1", LEASE, WORKER_IDENTITY);

            assertEquals("worker-1", locked.task().lockOwner());
            assertEquals("BK-AG-ID-1", locked.businessKey());
            assertEquals(List.of("conversationId"), List.copyOf(locked.variables().keySet()));
            assertInstanceOf(AgentTaskDefinition.class, locked.node());
            assertEquals(1, events(instance.id(), CriticalEventType.EXTERNAL_TASK_LOCKED).size());
            assertTrue(testEngine.repository().createExternalTaskQuery()
                    .topicName(TOPIC).fetchableAt(Instant.now()).list().isEmpty(), "Travada, sai da lista.");
        }

        @Test
        @DisplayName("e outro worker já tem o lease, então recebe 409; vencido o lease, trava")
        void otherWorkersLeaseIsRespected() throws InterruptedException {
            ExternalTask task = mission(start("BK-AG-ID-2").id());
            testEngine.engine().lockExternalTask(task.id(), "worker-1", Duration.ofMillis(30), WORKER_IDENTITY);

            assertThrows(ExternalTaskLockException.class,
                    () -> testEngine.engine().lockExternalTask(task.id(), "worker-2", LEASE, WORKER_IDENTITY));

            Thread.sleep(60);
            assertEquals("worker-2", testEngine.engine()
                    .lockExternalTask(task.id(), "worker-2", LEASE, WORKER_IDENTITY).task().lockOwner());
        }

        @Test
        @DisplayName("e o mesmo worker trava de novo, então só renova o lease, sem outro evento de lock")
        void sameWorkerRenews() {
            ProcessInstance instance = start("BK-AG-ID-3");
            ExternalTask task = mission(instance.id());
            Instant first = testEngine.engine().lockExternalTask(task.id(), "worker-1", Duration.ofMinutes(1),
                    WORKER_IDENTITY).task().lockExpiresAt();

            Instant renewed = testEngine.engine().lockExternalTask(task.id(), "worker-1", LEASE, WORKER_IDENTITY)
                    .task().lockExpiresAt();

            assertTrue(renewed.isAfter(first));
            assertEquals(1, events(instance.id(), CriticalEventType.EXTERNAL_TASK_LOCKED).size());
        }

        @Test
        @DisplayName("e a missão é de outro tenant, ou não existe, então é recusada")
        void otherTenantOrMissing() {
            ExternalTask task = mission(start("BK-AG-ID-4").id());

            assertThrows(SecurityException.class, () -> testEngine.engine().lockExternalTask(task.id(), "worker-1",
                    LEASE, new IdentityContext("worker", "outro-tenant")));
            assertThrows(TaskNotFoundException.class,
                    () -> testEngine.engine().lockExternalTask("nao-existe", "worker-1", LEASE, WORKER_IDENTITY));
            assertNull(mission(task.processInstanceId()).lockOwner());
        }

        @Test
        @DisplayName("e a missão já esgotou as tentativas, então não está disponível (409)")
        void exhaustedRetriesAreNotAvailable() {
            ExternalTask task = mission(start("BK-AG-ID-5").id());
            testEngine.engine().lockExternalTask(task.id(), "worker-1", LEASE, WORKER_IDENTITY);
            testEngine.engine().handleFailure(task.id(), "worker-1", "fora do ar", null, 0, null, WORKER_IDENTITY);

            assertThrows(ExternalTaskLockException.class,
                    () -> testEngine.engine().lockExternalTask(task.id(), "worker-2", LEASE, WORKER_IDENTITY));
        }
    }

    @Nested
    @DisplayName("Quando o worker conclui a missão")
    class WhenTheWorkerCompletes {

        @Test
        @DisplayName("com o contrato válido, então o processo segue pela rota do resultado e grava AGENT_MISSION_COMPLETED com o digest")
        void completesAndAudits() {
            ProcessInstance instance = start("BK-AG-10");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().completeExternalTask(locked.task().id(), "worker-1",
                    vars("resultado", "AGENDADO", "fotosVeiculo", List.of(photo("frente", "image/jpeg"))),
                    Map.of("turns", 12), WORKER_IDENTITY);

            assertTrue(reachedNode(instance.id(), "FIM_AGENDADO"));
            List<OutboxEventEntity> completed = events(instance.id(), CriticalEventType.AGENT_MISSION_COMPLETED);
            assertEquals(1, completed.size());
            AgentActivityRecorded payload = (AgentActivityRecorded) completed.get(0).getPayload();
            assertEquals("worker-1", payload.workerId());
            assertEquals(List.of("resultado", "fotosVeiculo"), payload.data().get("outputs"));
            assertEquals(64, payload.data().get("outputsDigest").toString().length());
            assertEquals(Map.of("turns", 12), payload.data().get("report"));
        }

        @Test
        @DisplayName("sem uma saída obrigatória, então recebe 422 e a missão continua em posse do worker")
        void rejectsMissingRequiredOutput() {
            ProcessInstance instance = start("BK-AG-11");
            LockedExternalTask locked = fetchOne("worker-1");

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(locked.task().id(), "worker-1", vars(), null, WORKER_IDENTITY));

            assertEquals("resultado", ex.getErrors().get(0).variable());
            assertEquals("worker-1", mission(instance.id()).lockOwner());
        }

        @Test
        @DisplayName("com uma foto em formato não aceito, então recebe INVALID_FILE_TYPE")
        void rejectsFileOfWrongType() {
            start("BK-AG-12");
            LockedExternalTask locked = fetchOne("worker-1");

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(locked.task().id(), "worker-1",
                            vars("resultado", "AGENDADO", "fotosVeiculo", List.of(photo("doc", "application/pdf"))),
                            null, WORKER_IDENTITY));

            assertEquals(VariableValidationError.INVALID_FILE_TYPE, ex.getErrors().get(0).code());
        }

        @Test
        @DisplayName("com mais fotos que kikwi:file.maxCount, então recebe TOO_MANY_FILES")
        void rejectsTooManyFiles() {
            start("BK-AG-13");
            LockedExternalTask locked = fetchOne("worker-1");
            List<Map<String, Object>> photos = List.of(photo("a", "image/jpeg"), photo("b", "image/png"),
                    photo("c", "image/jpeg"), photo("d", "image/jpeg"));

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(locked.task().id(), "worker-1",
                            vars("resultado", "AGENDADO", "fotosVeiculo", photos), null, WORKER_IDENTITY));

            assertEquals(VariableValidationError.TOO_MANY_FILES, ex.getErrors().get(0).code());
        }

        @Test
        @DisplayName("sendo outro worker, ou sem workerId, então a conclusão é recusada")
        void onlyTheLeaseOwnerCompletes() {
            ProcessInstance instance = start("BK-AG-14");
            LockedExternalTask locked = fetchOne("worker-1");
            String taskId = locked.task().id();

            assertThrows(ExternalTaskLockException.class, () -> testEngine.engine().completeExternalTask(taskId, "worker-2",
                    vars("resultado", "AGENDADO"), null, WORKER_IDENTITY));
            assertThrows(ExternalTaskLockException.class, () -> testEngine.engine().completeExternalTask(taskId,
                    vars("resultado", "AGENDADO"), new IdentityContext("operador", null)));
            assertNotNull(mission(instance.id()));
        }
    }

    @Nested
    @DisplayName("Quando o worker reporta falha técnica")
    class WhenTheWorkerFails {

        @Test
        @DisplayName("com retries restantes, então a missão volta à fila sem lease e com a mensagem de erro")
        void retriesWhileThereAreAttemptsLeft() {
            ProcessInstance instance = start("BK-AG-20");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().handleFailure(locked.task().id(), "worker-1", "LLM indisponível", null, 2, Duration.ZERO, WORKER_IDENTITY);

            ExternalTask failed = mission(instance.id());
            assertNull(failed.lockOwner());
            assertEquals(2, failed.retries());
            assertEquals("LLM indisponível", failed.errorMessage());
            assertEquals(1, events(instance.id(), CriticalEventType.EXTERNAL_TASK_FAILED).size());
            assertEquals("worker-2", fetchOne("worker-2").task().lockOwner());
        }

        @Test
        @DisplayName("sem retries, então abre incidente, a missão não é mais adquirível, e o retry do incidente a devolve")
        void opensAnIncidentAndRetryRestoresTheMission() {
            ProcessInstance instance = start("BK-AG-21");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().handleFailure(locked.task().id(), "worker-1", "Erro fatal", "stack", 0, null, WORKER_IDENTITY);

            List<Incident> incidents = testEngine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(1, incidents.size());
            assertEquals("FAILED_EXTERNAL_TASK", incidents.get(0).type());
            assertEquals(locked.task().id(), incidents.get(0).executionId());
            assertTrue(testEngine.engine().fetchAndLock("worker-2", List.of(TOPIC), 1, LEASE, WORKER_IDENTITY).isEmpty());

            testEngine.engine().retryIncident(incidents.get(0).id(), new IdentityContext("operador", null));

            assertEquals(IncidentStatus.RESOLVED,
                    testEngine.repository().findIncidentById(incidents.get(0).id()).orElseThrow().status());
            assertEquals("worker-2", fetchOne("worker-2").task().lockOwner());
        }
    }

    @Nested
    @DisplayName("Quando o worker lança um erro de negócio")
    class WhenTheWorkerThrowsABusinessError {

        @Test
        @DisplayName("com errorCode tratado, então o processo segue pelo error handler sem exigir as saídas")
        void followsTheErrorHandler() {
            ProcessInstance instance = start("BK-AG-30");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().throwBusinessError(locked.task().id(), "worker-1", "OPT_OUT", "Lead pediu para sair",
                    null, WORKER_IDENTITY);

            assertTrue(reachedNode(instance.id(), "FIM_OPT_OUT"));
            assertNull(mission(instance.id()));
            assertTrue(events(instance.id(), CriticalEventType.FLOW_NODE_FINISHED).stream()
                    .map(event -> (FlowNodeFinished) event.getPayload())
                    .anyMatch(node -> "QUALIFICAR".equals(node.getFlowNodeDefinitionId())
                            && "OPT_OUT".equals(node.getInterruptedByNodeDefinitionId())));
        }

        @Test
        @DisplayName("com errorCode sem tratador, então abre incidente UNHANDLED_BUSINESS_ERROR e a missão fica parada")
        void unhandledErrorBecomesIncident() {
            ProcessInstance instance = start("BK-AG-31");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().throwBusinessError(locked.task().id(), "worker-1", "DESCONHECIDO", null, null, WORKER_IDENTITY);

            assertEquals("UNHANDLED_BUSINESS_ERROR",
                    testEngine.repository().findIncidentsByProcessInstanceId(instance.id()).get(0).type());
            assertNotNull(mission(instance.id()));
            assertTrue(testEngine.engine().fetchAndLock("worker-2", List.of(TOPIC), 1, LEASE, WORKER_IDENTITY).isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando o runtime reporta atividades")
    class WhenTheRuntimeReportsActivities {

        @Test
        @DisplayName("então cada uma vira um evento AGENT_* com o workerId e os dados")
        void recordsActivities() {
            ProcessInstance instance = start("BK-AG-40");
            LockedExternalTask locked = fetchOne("worker-1");

            testEngine.engine().reportActivities(locked.task().id(), "worker-1", List.of(
                    new AgentActivityReport("AGENT_MISSION_STARTED", null, Map.of("agentVersion", 3, "promptHash", "a1b2c3")),
                    new AgentActivityReport("AGENT_TOOL_INVOKED", null, Map.of("toolId", "agendar_visita", "outcome", "OK"))),
                    WORKER_IDENTITY);

            AgentActivityRecorded tool = (AgentActivityRecorded) events(instance.id(), CriticalEventType.AGENT_TOOL_INVOKED)
                    .get(0).getPayload();
            assertEquals("agendar_visita", tool.data().get("toolId"));
            assertEquals("worker-1", tool.actorId());
            assertEquals(1, events(instance.id(), CriticalEventType.AGENT_MISSION_STARTED).size());
        }

        @Test
        @DisplayName("com tipo reservado ao motor ou dados grandes demais, então a chamada é recusada sem gravar nada")
        void rejectsInvalidActivities() {
            ProcessInstance instance = start("BK-AG-41");
            String taskId = fetchOne("worker-1").task().id();

            assertThrows(IllegalArgumentException.class, () -> testEngine.engine().reportActivities(taskId, "worker-1",
                    List.of(new AgentActivityReport("AGENT_MISSION_COMPLETED", null, Map.of())), WORKER_IDENTITY));
            assertThrows(IllegalArgumentException.class, () -> testEngine.engine().reportActivities(taskId, "worker-1",
                    List.of(new AgentActivityReport("AGENT_HANDOFF", null, Map.of("transcript", "x".repeat(9000)))),
                    WORKER_IDENTITY));
            assertTrue(events(instance.id(), CriticalEventType.AGENT_HANDOFF).isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando um humano assume a conversa durante o lease")
    class WhenAHumanTakesOver {

        @Test
        @DisplayName("então o catch interruptivo encerra a missão, grava EVENT_CORRELATED e o worker recebe 404 ao renovar")
        void boundaryInterruptsTheLeasedMission() {
            ProcessInstance instance = start("BK-AG-50");
            String taskId = fetchOne("worker-1").task().id();

            testEngine.engine().correlateMessage("humano-assumiu-1", vars("atendente", "maria"), new IdentityContext("crm", null));

            assertTrue(reachedNode(instance.id(), "FIM_HUMANO"));
            EventCorrelated correlated = (EventCorrelated) events(instance.id(), CriticalEventType.EVENT_CORRELATED)
                    .get(0).getPayload();
            assertEquals("humano-assumiu-1", correlated.correlationKey());
            assertEquals("maria", correlated.submittedVariables().get("atendente").value());
            assertThrows(TaskNotFoundException.class, () ->
                    testEngine.engine().extendLock(taskId, "worker-1", LEASE, WORKER_IDENTITY));
        }

        @Test
        @DisplayName("e o worker renova antes disso, então o lease é estendido")
        void extendLockKeepsTheLease() {
            ProcessInstance instance = start("BK-AG-51");
            LockedExternalTask locked = fetchOne("worker-1");

            ExternalTask renewed = testEngine.engine().extendLock(locked.task().id(), "worker-1", Duration.ofHours(1), WORKER_IDENTITY);

            assertTrue(renewed.lockExpiresAt().isAfter(locked.task().lockExpiresAt()));
            assertEquals(renewed.lockExpiresAt(), mission(instance.id()).lockExpiresAt());
            assertThrows(ExternalTaskLockException.class, () ->
                    testEngine.engine().extendLock(locked.task().id(), "worker-2", LEASE, WORKER_IDENTITY));
            assertFalse(testEngine.engine().fetchAndLock("worker-2", List.of(TOPIC), 1, LEASE, WORKER_IDENTITY).size() > 0);
        }
    }
}
