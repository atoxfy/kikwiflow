/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.rule.api.DecisionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Responsável pela lógica de navegação dentro do grafo de um processo BPMN.
 * <p>
 * Esta classe encapsula as regras para determinar qual o próximo nó a ser executado,
 * com base no estado atual do processo e na estrutura da definição do processo.
 * Ela lida com a travessia dos fluxos de sequência (sequence flows) e a identificação
 * de pontos de início e continuação.
 */
public class Navigator {


    private final DecisionRuleResolver decisionRuleResolver;

    public Navigator(DecisionRuleResolver decisionRuleResolver) {
        this.decisionRuleResolver = decisionRuleResolver;
    }


    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessDefinition processDefinition, Map<String, ProcessVariable> variables, boolean forceAsync, String targetFlowId) {
        //todo mudar para usar logica de optional.or
        List<SequenceFlowDefinition> outgoingFlows = completedNode.outgoing();

        if (outgoingFlows.isEmpty()) {
            return null;
        }

        List<FlowNodeDefinition> nextNodes = new ArrayList<>();
        if (completedNode instanceof ExclusiveGatewayDefinition gateway) {
            Optional<SequenceFlowDefinition> chosenFlow = Optional.empty();

            if (targetFlowId != null && !targetFlowId.isBlank()) {
                chosenFlow = outgoingFlows.stream().filter(sf -> sf.id().equals(targetFlowId)).findFirst();
                if (chosenFlow.isEmpty()) {
                    throw new IllegalArgumentException("Invalid targetFlowId: '" + targetFlowId + "' is not a valid outgoing flow for gateway '" + gateway.id() + "'.");
                }
            }

            if (chosenFlow.isEmpty()) {
                for (SequenceFlowDefinition flow : outgoingFlows) {
                    if (flow.condition() != null && !flow.condition().isBlank()) {
                        DecisionRule decisionRule = decisionRuleResolver.resolve(flow.condition()).orElseThrow(
                                () -> new DecisionRuleNotFoundException("DecisionRule not found with key: " + flow.condition()));

                        if (decisionRule.evaluate(variables)) {
                            chosenFlow = Optional.of(flow);
                            break;
                        }
                    }
                }
            }

            if (chosenFlow.isEmpty()) {
                String defaultFlowId = gateway.defaultFlow();
                if (defaultFlowId != null) {
                    chosenFlow = outgoingFlows.stream().filter(sf -> sf.id().equals(defaultFlowId)).findFirst();
                }
            }

            if (chosenFlow.isPresent()) {
                nextNodes.add(processDefinition.flowNodes().get(chosenFlow.get().targetNodeId()));
            } else {
                throw new IllegalStateException("Execution Error: Exclusive gateway '" + gateway.id() + "' has no valid outgoing sequence flow for the given variables.");
            }

        } else {
            String targetNodeId = outgoingFlows.get(0).targetNodeId();
            nextNodes.add(processDefinition.flowNodes().get(targetNodeId));
        }

        boolean isAsync = false;
        if (forceAsync) {
            isAsync = true;
        } else {
            isAsync = Boolean.TRUE.equals(nextNodes.get(0).commitBefore());
        }

        return new Continuation(nextNodes, isAsync);
    }
}
