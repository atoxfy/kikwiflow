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
package io.kikwiflow.navigation;

import io.kikwiflow.exception.DecisionRuleNotFoundException;
import io.kikwiflow.execution.DecisionRuleResolver;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.*;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.rule.api.DecisionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BDD Tests - Navigator Continuation Flow")
class NavigatorTest {

    @Mock
    private DecisionRuleResolver decisionRuleResolver;

    @InjectMocks
    private Navigator navigator;

    @Mock
    private ProcessDefinition processDefinition;

    private Map<String, ProcessVariable> variables;

    @BeforeEach
    void setUp() {
        variables = Collections.emptyMap();
    }

    @Nested
    @DisplayName("Cenários de Fluxos Lineares Comuns")
    class LinearFlowScenarios {

        @Test
        @DisplayName("Given um nó sem fluxos de saída (End Event), When determinar a continuação, Then deve retornar null")
        void givenEndEventWithNoOutgoingFlows_whenDetermineContinuation_thenReturnsNull() {
            // Given
            EndEventDefinition endEvent = EndEventDefinition.builder()
                    .id("FIM")
                    .outgoing(Collections.emptyList())
                    .build();

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    endEvent, processDefinition, variables, false, null);

            // Then
            assertNull(continuation);
        }

        @Test
        @DisplayName("Given um nó de tarefa simples com destino válido, When determinar a continuação, Then deve avançar para o próximo nó")
        void givenSimpleTaskWithValidTarget_whenDetermineContinuation_thenAdvanceToNextNode() {
            // Given
            SequenceFlowDefinition sequenceFlow = new SequenceFlowDefinition("flow-1", null, "TASK_SUCESSORA", false, List.of());

            StartEventDefinition startEvent = StartEventDefinition.builder()
                    .id("INICIO")
                    .outgoing(List.of(sequenceFlow))
                    .build();

            ExecutableTaskDefinition nextTask = ExecutableTaskDefinition.builder()
                    .id("TASK_SUCESSORA")
                    .commitBefore(false)
                    .build();

            when(processDefinition.flowNodes()).thenReturn(Map.of("TASK_SUCESSORA", nextTask));

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    startEvent, processDefinition, variables, false, null);

            // Then
            assertNotNull(continuation);
            assertEquals(1, continuation.nextNodes().size());
            assertEquals(nextTask, continuation.nextNodes().get(0));
            assertFalse(continuation.isAsynchronous());
        }

        @Test
        @DisplayName("Given a flag forceAsync ativa, When determinar a continuação, Then o retorno deve ser assíncrono independente do nó")
        void givenForceAsyncTrue_whenDetermineContinuation_thenContinuationIsAsync() {
            // Given
            SequenceFlowDefinition sequenceFlow = new SequenceFlowDefinition("flow-1", null, "TASK_SUCESSORA", false, List.of());
            StartEventDefinition startEvent = StartEventDefinition.builder().id("INICIO").outgoing(List.of(sequenceFlow)).build();
            ExecutableTaskDefinition nextTask = ExecutableTaskDefinition.builder().id("TASK_SUCESSORA").commitBefore(false).build();

            when(processDefinition.flowNodes()).thenReturn(Map.of("TASK_SUCESSORA", nextTask));

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    startEvent, processDefinition, variables, true, null);

            // Then
            assertTrue(continuation.isAsynchronous());
        }
    }

    @Nested
    @DisplayName("Cenários de Exclusive Gateway (Decisões de Roteamento)")
    class ExclusiveGatewayScenarios {

        @Test
        @DisplayName("Given um desvio forçado por targetFlowId válido, When determinar a continuação, Then deve ir direto para o alvo sem avaliar regras")
        void givenValidTargetFlowId_whenDetermineContinuation_thenRouteDirectlyWithoutRules() {
            // Given
            SequenceFlowDefinition flowA = new SequenceFlowDefinition("flowA", "regraA", "NODE_A", false, List.of());
            SequenceFlowDefinition flowB = new SequenceFlowDefinition("flowB", "regraB", "NODE_B", false, List.of());

            ExclusiveGatewayDefinition gateway = ExclusiveGatewayDefinition.builder()
                    .id("GATEWAY_DECISAO")
                    .outgoing(List.of(flowA, flowB))
                    .build();

            ExternalTaskDefinition nodeB = ExternalTaskDefinition.builder().id("NODE_B").commitBefore(true).build();
            when(processDefinition.flowNodes()).thenReturn(Map.of("NODE_B", nodeB));

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    gateway, processDefinition, variables, false, "NODE_B");

            // Then
            assertNotNull(continuation);
            assertEquals(nodeB, continuation.nextNodes().get(0));
            verifyNoInteractions(decisionRuleResolver);
        }

        @Test
        @DisplayName("Given um desvio forçado por targetFlowId inválido, When determinar a continuação, Then deve lançar IllegalArgumentException (Fail-Fast)")
        void givenInvalidTargetFlowId_whenDetermineContinuation_thenThrowIllegalArgumentException() {
            // Given
            SequenceFlowDefinition flowA = new SequenceFlowDefinition("flowA", null, "NODE_A", false, List.of());
            ExclusiveGatewayDefinition gateway = ExclusiveGatewayDefinition.builder()
                    .id("GATEWAY_TESTE")
                    .outgoing(List.of(flowA))
                    .build();

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                navigator.determineNextContinuation(
                        gateway, processDefinition, variables, false, "ID_INEXISTENTE");
            });

            assertTrue(exception.getMessage().contains("ID_INEXISTENTE"));
            assertTrue(exception.getMessage().contains("GATEWAY_TESTE"));
        }

        @Test
        @DisplayName("Given regras condicionais no gateway, When uma regra for avaliada como verdadeira, Then deve seguir o fluxo correspondente")
        void givenConditionalRules_whenRuleEvaluatesTrue_thenFollowMatchingFlow() {
            // Given
            SequenceFlowDefinition flowA = new SequenceFlowDefinition("flowA", "condicao_A", "NODE_A", false, List.of());
            SequenceFlowDefinition flowB = new SequenceFlowDefinition("flowB", "condicao_B", "NODE_B", false, List.of());

            ExclusiveGatewayDefinition gateway = ExclusiveGatewayDefinition.builder()
                    .id("GATEWAY_REGRAS")
                    .outgoing(List.of(flowA, flowB))
                    .build();

            DecisionRule ruleA = mock(DecisionRule.class);
            when(ruleA.evaluate(variables)).thenReturn(false);

            DecisionRule ruleB = mock(DecisionRule.class);
            when(ruleB.evaluate(variables)).thenReturn(true);

            when(decisionRuleResolver.resolve("condicao_A")).thenReturn(Optional.of(ruleA));
            when(decisionRuleResolver.resolve("condicao_B")).thenReturn(Optional.of(ruleB));

            ExecutableTaskDefinition nodeB = ExecutableTaskDefinition.builder().id("NODE_B").build();
            when(processDefinition.flowNodes()).thenReturn(Map.of("NODE_B", nodeB));

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    gateway, processDefinition, variables, false, null);

            // Then
            assertNotNull(continuation);
            assertEquals(nodeB, continuation.nextNodes().get(0));
        }

        @Test
        @DisplayName("Given que todas as regras falharam, When houver um defaultFlow configurado, Then deve seguir a rota padrão")
        void givenAllRulesFail_whenDefaultFlowExists_thenFallbackToDefaultRoute() {
            // Given
            SequenceFlowDefinition conditionalFlow = new SequenceFlowDefinition("flow-cond", "condicao_falsa", "NODE_A", false, List.of());
            SequenceFlowDefinition defaultFlow = new SequenceFlowDefinition("flow-default", null, "NODE_DEFAULT", false, List.of());

            ExclusiveGatewayDefinition gateway = ExclusiveGatewayDefinition.builder()
                    .id("GATEWAY_DEFAULT")
                    .outgoing(List.of(conditionalFlow, defaultFlow))
                    .defaultFlow("flow-default") // Aponta para o ID da aresta padrão
                    .build();

            DecisionRule ruleFalsa = mock(DecisionRule.class);
            when(ruleFalsa.evaluate(variables)).thenReturn(false);
            when(decisionRuleResolver.resolve("condicao_falsa")).thenReturn(Optional.of(ruleFalsa));

            ExternalTaskDefinition nodeDefault = ExternalTaskDefinition.builder().id("NODE_DEFAULT").build();
            when(processDefinition.flowNodes()).thenReturn(Map.of("NODE_DEFAULT", nodeDefault));

            // When
            Continuation continuation = navigator.determineNextContinuation(
                    gateway, processDefinition, variables, false, null);

            // Then
            assertNotNull(continuation);
            assertEquals(nodeDefault, continuation.nextNodes().get(0));
        }

        @Test
        @DisplayName("Given que nenhuma rota bateu e não há fluxo padrão, When avaliar o gateway, Then deve lançar IllegalStateException")
        void givenNoMatchingRoutesAndNoDefault_whenEvaluateGateway_thenThrowIllegalStateException() {
            // Given
            SequenceFlowDefinition conditionalFlow = new SequenceFlowDefinition("flow-cond", "condicao_falsa", "NODE_A", false, List.of());

            ExclusiveGatewayDefinition gateway = ExclusiveGatewayDefinition.builder()
                    .id("GATEWAY_TRAVADO")
                    .outgoing(List.of(conditionalFlow))
                    .defaultFlow(null)
                    .build();

            DecisionRule ruleFalsa = mock(DecisionRule.class);
            when(ruleFalsa.evaluate(variables)).thenReturn(false);
            when(decisionRuleResolver.resolve("condicao_falsa")).thenReturn(Optional.of(ruleFalsa));

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                navigator.determineNextContinuation(
                        gateway, processDefinition, variables, false, null);
            });

            assertTrue(exception.getMessage().contains("has no valid outgoing sequence flow"));
            assertTrue(exception.getMessage().contains("GATEWAY_TRAVADO"));
        }
    }
}
