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
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Documenta as regras novas promovidas de "Sugerida" para "Existente" em
 * docs/engine/14-regras-de-processo-valido.md nesta revisão — cada teste deploya um fixture mínimo que viola
 * exatamente uma regra e confirma que {@code DeployValidator} agora rejeita no deploy, não só em runtime (ver
 * docs/engine/15-achados-motor-lacunas-de-validacao.md para o racional/evidência original de cada uma).
 */
@DisplayName("Dado um processo violando uma das regras de validação promovidas nesta revisão")
class NewDeployValidatorRulesTest {

    private static RuntimeException deployAndCapture(TestEngine testEngine, String fixture) {
        return assertThrows(RuntimeException.class, () -> testEngine.deploy(fixture));
    }

    @Test
    @DisplayName("KIKWI-001: deploy rejeita sequence flow apontando para targetNodeId inexistente")
    void deployRejectsSequenceFlowTargetingMissingNode() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/sequence-flow-target-missing-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-003: deploy rejeita defaultStartPoint apontando para chave inexistente")
    void deployRejectsDefaultStartPointTargetingMissingNode() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/default-start-point-missing-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-009: deploy rejeita DEFAULT_START_EVENT com mais de uma saída")
    void deployRejectsStartEventWithMultipleOutgoing() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/start-event-two-outgoing-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-011: deploy rejeita EXECUTABLE_TASK sem 'executor'")
    void deployRejectsExecutableTaskWithBlankExecutor() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/executable-task-blank-executor-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-019: deploy rejeita EXCLUSIVE_GATEWAY com mais de uma aresta handlesNull")
    void deployRejectsGatewayWithMultipleHandlesNullFlows() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/exclusive-gateway-duplicate-handles-null-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-020: deploy rejeita EXCLUSIVE_GATEWAY com expectedAnswer duplicado")
    void deployRejectsGatewayWithDuplicateExpectedAnswer() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/exclusive-gateway-duplicate-expected-answer-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-021: deploy rejeita EXCLUSIVE_GATEWAY sem nenhuma saída")
    void deployRejectsGatewayWithNoOutgoing() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/exclusive-gateway-no-outgoing-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-024: deploy rejeita PARALLEL_GATEWAY sem targetJoinId")
    void deployRejectsParallelGatewayWithoutTargetJoinId() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/parallel-gateway-missing-target-join-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-025: deploy rejeita PARALLEL_GATEWAY com targetJoinId inexistente")
    void deployRejectsParallelGatewayWithUnresolvedTargetJoinId() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/parallel-gateway-target-join-not-found-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-026: deploy rejeita PARALLEL_GATEWAY com targetJoinId de tipo errado")
    void deployRejectsParallelGatewayWithWrongTypeTargetJoinId() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/parallel-gateway-target-join-wrong-type-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-033: deploy rejeita BOUNDARY_INTERRUPTIVE_TIMER sem providerType")
    void deployRejectsInterruptiveTimerWithoutProviderType() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/boundary-interruptive-timer-missing-provider-type-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-037: deploy rejeita BOUNDARY_NON_INTERRUPTIVE_TIMER sem schedulePolicy")
    void deployRejectsNonInterruptiveTimerWithoutSchedulePolicy() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/boundary-non-interruptive-timer-missing-schedule-policy-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-039: deploy rejeita schedulePolicy RATE_DURATION sem expression")
    void deployRejectsSchedulePolicyRateDurationWithoutExpression() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/boundary-non-interruptive-timer-rate-duration-missing-expression-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-040: deploy rejeita schedulePolicy FIXED_DATES com fixedDates vazio")
    void deployRejectsSchedulePolicyFixedDatesEmpty() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/boundary-non-interruptive-timer-fixed-dates-empty-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Fecha o gap ao lado de KIKWI-041: deploy rejeita BOUNDARY_ERROR_HANDLER anexado a EXTERNAL_TASK")
    void deployRejectsErrorHandlerAttachedToExternalTask() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/external-task-boundary-error-handler-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    // Os 4 fixtures abaixo declaram um 'executor' não-vazio ("processarPagamentoTaskHandler") — sem registrar
    // o bean, o deploy já falharia antes na checagem de resolução do TaskHandler (KIKWI-012, já Existente),
    // sem chegar a exercitar de fato a regra que cada teste quer provar.

    @Test
    @DisplayName("KIKWI-042: deploy rejeita dois BOUNDARY_ERROR_HANDLER com o mesmo errorCode no mesmo nó pai")
    void deployRejectsDuplicateErrorCodeOnSameParent() {
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("processarPagamentoTaskHandler", ctx -> {})
                .build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/executable-task-duplicate-error-code-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-043: deploy rejeita retryPolicy sem maxRetries explícito (desserializa como 0)")
    void deployRejectsRetryPolicyWithoutExplicitMaxRetries() {
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("processarPagamentoTaskHandler", ctx -> {})
                .build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/executable-task-retry-policy-zero-max-retries-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-044: deploy rejeita retryPolicy sem strategy reconhecida")
    void deployRejectsRetryPolicyWithoutStrategy() {
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("processarPagamentoTaskHandler", ctx -> {})
                .build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/executable-task-retry-policy-missing-strategy-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("KIKWI-045: deploy rejeita retryPolicy EXPONENTIAL_BACKOFF sem initialInterval")
    void deployRejectsExponentialBackoffWithoutInitialInterval() {
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("processarPagamentoTaskHandler", ctx -> {})
                .build();
        RuntimeException thrown = deployAndCapture(testEngine, "/processes/executable-task-retry-policy-exponential-missing-interval-invalid.json");
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }
}
