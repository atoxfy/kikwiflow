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

import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o caminho de falha/retry/incidente de uma {@code EXECUTABLE_TASK} assíncrona
 * ({@code commitBefore: true}), dirigido manualmente via {@code engine.executeFromTask(...)} — ver
 * "Testando o Motor" para o porquê dessa condução manual em vez de depender do {@code TaskAcquirer}.
 * <p>
 * Cobre duas garantias que não tinham teste de referência antes: (1) uma falha de nó ainda produz o outbox
 * event {@code FLOW_NODE_FINISHED(ERROR)} mesmo quando a exceção precisa propagar para acionar o
 * {@code FailureHandler} — o evento não pode ser descartado só porque a execução terminou em exceção em vez
 * de sucesso; e (2) {@code retryIncident} devolve à tarefa o orçamento de retries declarado na
 * {@code RetryPolicy} do próprio nó, não um valor fixo.
 */
@DisplayName("Dado um processo com uma tarefa executável assíncrona com RetryPolicy declarada")
class RetryAndIncidentFlowTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final AtomicInteger taskHandlerCalls = new AtomicInteger(0);
    private final AtomicInteger failuresBeforeSucceeding = new AtomicInteger(Integer.MAX_VALUE);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withConfig(config -> config.setOutboxEventsEnabled(true))
                .withTaskHandler("calculateRisk", ctx -> {
                    int callNumber = taskHandlerCalls.incrementAndGet();
                    if (callNumber <= failuresBeforeSucceeding.get()) {
                        throw new RuntimeException("Falha simulada de cálculo de risco (tentativa " + callNumber + ")");
                    }
                })
                .build();

        definition = testEngine.deploy("/processes/retryable-executable-task-flow.json");
    }

    private ExecutableTask fetchPendingTask(String processInstanceId) {
        List<ExecutableTask> tasks = testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId);
        assertEquals(1, tasks.size(), "Deveria haver exatamente uma ExecutableTask pendente.");
        return tasks.get(0);
    }

    @Nested
    @DisplayName("Quando o TaskHandler falha e ainda há orçamento de retries na RetryPolicy do nó (maxRetries: 2)")
    class WhenTheTaskHandlerFailsWithRetryBudgetRemaining {

        @Test
        @DisplayName("Então a tarefa é reagendada com retries decrementados, sem abrir incidente, e o FLOW_NODE_FINISHED(ERROR) chega ao outbox")
        void reschedulesTheTaskAndStillEmitsTheErrorEvent() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-RETRY-1")
                    .execute();

            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

            ExecutableTask pendingTask = fetchPendingTask(instance.id());
            assertEquals(2L, pendingTask.retries(), "Retries iniciais deveriam vir da RetryPolicy do nó (maxRetries: 2), não do fallback global.");

            testEngine.engine().executeFromTask(pendingTask);

            ExecutableTask afterFirstFailure = fetchPendingTask(instance.id());
            assertEquals(ExecutableTaskStatus.PENDING, afterFirstFailure.status());
            assertEquals(1L, afterFirstFailure.retries());

            assertTrue(testEngine.repository().findIncidentsByProcessInstanceId(instance.id()).isEmpty(),
                    "Ainda não deveria haver incidente enquanto restam retries.");

            List<OutboxEventEntity> events = testEngine.repository().findEventHistoryByProcessInstanceId(instance.id());
            OutboxEventEntity errorEvent = events.stream()
                    .filter(e -> e.getPayload() instanceof FlowNodeFinished flowNodeFinished
                            && flowNodeFinished.getNodeExecutionStatus() == NodeExecutionStatus.ERROR)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Esperava um outbox event FLOW_NODE_FINISHED(ERROR) mesmo com a execução terminando em exceção."));

            FlowNodeFinished errorPayload = (FlowNodeFinished) errorEvent.getPayload();
            assertEquals("CALCULATE_RISK_TASK", errorPayload.getFlowNodeDefinitionId());
            assertEquals(definition.key(), errorPayload.getProcessDefinitionKey(),
                    "O evento deveria carregar a key estável da definição, não só o id interno da versão.");
            assertTrue(errorPayload.getErrorMessage().contains("Falha simulada"));
        }
    }

    @Nested
    @DisplayName("Quando o TaskHandler falha até esgotar a RetryPolicy do nó (maxRetries: 2)")
    class WhenRetriesAreExhausted {

        @Test
        @DisplayName("Então um incidente OPEN é criado, e retryIncident restaura retries=maxRetries da política do nó (não um valor fixo)")
        void opensAnIncidentAndManualRetryRestoresThePolicyBudget() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-RETRY-2")
                    .execute();

            ExecutableTask task = fetchPendingTask(instance.id());
            testEngine.engine().executeFromTask(task); // 1ª falha: retries 2 -> 1, reagenda

            task = fetchPendingTask(instance.id());
            testEngine.engine().executeFromTask(task); // 2ª falha: retries 1 -> 0, esgota -> incidente

            ExecutableTask failedTask = fetchPendingTask(instance.id());
            assertEquals(ExecutableTaskStatus.ERROR, failedTask.status());
            assertEquals(0L, failedTask.retries());

            List<Incident> incidents = testEngine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(1, incidents.size());
            Incident incident = incidents.get(0);
            assertEquals(IncidentStatus.OPEN, incident.status());
            assertEquals("FAILED_JOB", incident.type());

            // Corrige a causa raiz e retenta manualmente, exatamente como um operador faria via
            // PUT /incidents/{id}/retry.
            failuresBeforeSucceeding.set(0);
            testEngine.engine().retryIncident(incident.id(), IdentityContext.system());

            ExecutableTask restoredTask = fetchPendingTask(instance.id());
            assertEquals(ExecutableTaskStatus.PENDING, restoredTask.status());
            assertEquals(2L, restoredTask.retries(),
                    "retryIncident deveria restaurar retries a partir de RetryPolicy.maxRetries() do nó (2), não de um valor fixo hardcoded.");

            List<Incident> incidentsAfterRetry = testEngine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(IncidentStatus.RESOLVED, incidentsAfterRetry.get(0).status());

            // O TaskHandler agora sucede (causa raiz corrigida) — o processo deve completar normalmente,
            // provando que o caminho de erro/retry/incidente não deixou o motor em um estado inconsistente.
            ProcessInstance completedInstance = testEngine.engine().executeFromTask(restoredTask);
            assertEquals(ProcessInstanceStatus.COMPLETED, completedInstance.status());
        }
    }
}
