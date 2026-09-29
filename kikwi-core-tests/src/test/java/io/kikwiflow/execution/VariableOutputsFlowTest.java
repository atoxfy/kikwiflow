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

import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.variable.VariableValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 2 do contrato de dados (docs/engine/26): {@code outputs} aplicados em runtime. Numa
 * {@code EXECUTABLE_TASK}, uma saída fora do contrato é tratada como falha do handler — retry pela RetryPolicy
 * do nó e, esgotado, incidente — antes de qualquer gateway consumir o valor. É o caso de uso "trilho para IA":
 * o handler chama um LLM e o motor não confia na resposta. Numa {@code EXTERNAL_TASK}, a falha é síncrona e a
 * tarefa continua pendente, como já acontece com {@code inputs}.
 */
@DisplayName("Dado um processo cuja tarefa executável e tarefa externa declaram outputs")
class VariableOutputsFlowTest {

    private static final IdentityContext ACTOR = new IdentityContext("test-actor", null);

    private TestEngine testEngine;
    private ProcessDefinition definition;
    /** O que o handler "analisar" grava na instância — cada teste define a resposta simulada da IA. */
    private final AtomicReference<Map<String, Object>> handlerOutputs = new AtomicReference<>(Map.of());

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withConfig(config -> config.setOutboxEventsEnabled(true))
                .withTaskHandler("analisar", ctx ->
                        handlerOutputs.get().forEach((key, value) -> ctx.setVariable(new ProcessVariable(key, value))))
                .build();
        definition = testEngine.deploy("/processes/variable-outputs.json");
    }

    private static Map<String, ProcessVariable> vars(Object... keyValues) {
        Map<String, ProcessVariable> variables = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            variables.put(key, new ProcessVariable(key, keyValues[i + 1]));
        }
        return variables;
    }

    private static Map<String, String> codesByVariable(VariableValidationException ex) {
        return ex.getErrors().stream()
                .collect(Collectors.toMap(VariableValidationError::variable, VariableValidationError::code));
    }

    private ProcessInstance start(String businessKey) {
        return testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey(businessKey)
                .execute();
    }

    private ExecutableTask pendingAnalysis(String processInstanceId) {
        List<ExecutableTask> tasks = testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId);
        assertEquals(1, tasks.size(), "Deveria haver exatamente uma ExecutableTask de análise.");
        return tasks.get(0);
    }

    private String reviewTaskId(String processInstanceId) {
        List<ExternalTask> tasks = testEngine.repository().findExternalTasksByProcessInstanceId(processInstanceId);
        assertEquals(1, tasks.size());
        return tasks.get(0).id();
    }

    @Nested
    @DisplayName("Quando o handler da tarefa executável devolve a saída")
    class WhenTheExecutableTaskProducesOutputs {

        @Test
        @DisplayName("completa (o output opcional pode faltar), então o fluxo segue para a tarefa externa")
        void proceedsWhenRequiredOutputsAreValid() {
            handlerOutputs.set(Map.of("decisao", "APROVAR", "confianca", 0.93));
            ProcessInstance instance = start("BK-OUT-1");

            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));

            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "REVISAR");
        }

        @Test
        @DisplayName("sem um output obrigatório, então a tarefa é reagendada, o FLOW_NODE_FINISHED(ERROR) cita o campo e o fluxo não avança")
        void retriesWhenARequiredOutputIsMissing() {
            handlerOutputs.set(Map.of("decisao", "APROVAR"));
            ProcessInstance instance = start("BK-OUT-2");

            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));

            ExecutableTask afterFailure = pendingAnalysis(instance.id());
            assertEquals(ExecutableTaskStatus.PENDING, afterFailure.status());
            assertEquals(1L, afterFailure.retries());
            assertTrue(testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).isEmpty(),
                    "Uma saída fora do contrato não pode deixar o fluxo avançar.");

            FlowNodeFinished error = testEngine.repository().findEventHistoryByProcessInstanceId(instance.id()).stream()
                    .map(e -> e.getPayload())
                    .filter(p -> p instanceof FlowNodeFinished f && f.getNodeExecutionStatus() == NodeExecutionStatus.ERROR)
                    .map(FlowNodeFinished.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Esperava FLOW_NODE_FINISHED(ERROR) para a saída inválida."));
            assertEquals("ANALISAR", error.getFlowNodeDefinitionId());
            assertTrue(error.getErrorMessage().contains("confianca=REQUIRED"), error.getErrorMessage());
        }

        @Test
        @DisplayName("em formato inválido até esgotar os retries, então abre incidente; corrigida a causa, o retry manual segue o fluxo")
        void opensAnIncidentWhenTheOutputStaysInvalid() {
            handlerOutputs.set(Map.of("decisao", "TALVEZ", "confianca", "alta"));
            ProcessInstance instance = start("BK-OUT-3");

            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));
            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));

            ExecutableTask failed = pendingAnalysis(instance.id());
            assertEquals(ExecutableTaskStatus.ERROR, failed.status());
            List<Incident> incidents = testEngine.repository().findIncidentsByProcessInstanceId(instance.id());
            assertEquals(1, incidents.size());
            assertEquals(IncidentStatus.OPEN, incidents.get(0).status());

            handlerOutputs.set(Map.of("decisao", "REPROVAR", "confianca", 0.4, "motivo", "Documento ilegível"));
            testEngine.engine().retryIncident(incidents.get(0).id(), IdentityContext.system());
            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));

            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "REVISAR");
        }
    }

    @Nested
    @DisplayName("Quando a tarefa externa é concluída")
    class WhenTheExternalTaskIsCompleted {

        private String reachReview(String businessKey) {
            handlerOutputs.set(Map.of("decisao", "APROVAR", "confianca", 0.93));
            ProcessInstance instance = start(businessKey);
            testEngine.engine().executeFromTask(pendingAnalysis(instance.id()));
            return instance.id();
        }

        @Test
        @DisplayName("sem o output obrigatório, então falha com REQUIRED e a tarefa continua pendente")
        void rejectsMissingRequiredOutput() {
            String instanceId = reachReview("BK-OUT-4");
            String taskId = reviewTaskId(instanceId);

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(taskId, vars("observacao", "ok"), ACTOR));

            assertEquals(Map.of("parecer", VariableValidationError.REQUIRED), codesByVariable(ex));
            testEngine.repository().assertHasActiveExternalTaskOn(instanceId, "REVISAR");
        }

        @Test
        @DisplayName("com o output obrigatório, então o processo conclui")
        void completesWithRequiredOutput() {
            String instanceId = reachReview("BK-OUT-5");

            ProcessInstance completed = testEngine.engine().completeExternalTask(reviewTaskId(instanceId),
                    vars("parecer", "Documento conferido"), ACTOR);

            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }
    }
}
