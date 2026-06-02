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

public class Navigator {


    private final DecisionRuleResolver decisionRuleResolver;

    public Navigator(DecisionRuleResolver decisionRuleResolver) {
        this.decisionRuleResolver = decisionRuleResolver;
    }


    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessDefinition processDefinition, Map<String, ProcessVariable> variables, boolean forceAsync, String targetFlowId) {
        List<SequenceFlowDefinition> outgoingFlows = completedNode.outgoing();

        if (outgoingFlows.isEmpty()) {
            return null;
        }

        List<FlowNodeDefinition> nextNodes = new ArrayList<>();

        if (completedNode instanceof ExclusiveGatewayDefinition gateway) {
            Optional<SequenceFlowDefinition> chosenFlow;

            if (targetFlowId != null && !targetFlowId.isBlank()) {
                chosenFlow = outgoingFlows.stream()
                        .filter(sf -> sf.targetNodeId().equals(targetFlowId))
                        .findFirst();

                if (chosenFlow.isEmpty()) {
                    throw new IllegalArgumentException("Execution Error: Forced targetFlowId '" + targetFlowId +
                            "' is not a valid outgoing path from gateway '" + gateway.id() + "'.");
                }
            } else {
                chosenFlow = evaluateConditions(outgoingFlows, variables)
                        .or(() -> evaluateDefaultFlow(gateway, outgoingFlows));
            }

            if (chosenFlow.isPresent()) {
                FlowNodeDefinition nextNode = processDefinition.flowNodes().get(chosenFlow.get().targetNodeId());
                if (nextNode == null) {
                    throw new IllegalStateException("Architectural Error: Target node '" + chosenFlow.get().targetNodeId() +
                            "' defined in sequence flow does not exist in the process definition.");
                }
                nextNodes.add(nextNode);
            } else {
                throw new IllegalStateException("Execution Error: Exclusive gateway '" + gateway.id() +
                        "' has no valid outgoing sequence flow for the given variables.");
            }

        } else {
            String targetNodeId = outgoingFlows.get(0).targetNodeId();
            FlowNodeDefinition nextNode = processDefinition.flowNodes().get(targetNodeId);
            if (nextNode == null) {
                throw new IllegalStateException("Architectural Error: Next node '" + targetNodeId + "' not found.");
            }
            nextNodes.add(nextNode);
        }

        boolean isAsync = forceAsync || Boolean.TRUE.equals(nextNodes.get(0).commitBefore());

        return new Continuation(nextNodes, isAsync);
    }

    // Métodos auxiliares para manter o método principal legível (Módulos Profundos)
    private Optional<SequenceFlowDefinition> evaluateConditions(List<SequenceFlowDefinition> outgoingFlows, Map<String, ProcessVariable> variables) {
        return outgoingFlows.stream()
                .filter(flow -> flow.condition() != null && !flow.condition().isBlank())
                .filter(flow -> {
                    DecisionRule decisionRule = decisionRuleResolver.resolve(flow.condition())
                            .orElseThrow(() -> new DecisionRuleNotFoundException("DecisionRule not found with key: " + flow.condition()));
                    return decisionRule.evaluate(variables);
                })
                .findFirst();
    }

    private Optional<SequenceFlowDefinition> evaluateDefaultFlow(ExclusiveGatewayDefinition gateway, List<SequenceFlowDefinition> outgoingFlows) {
        String defaultFlowId = gateway.defaultFlow();
        if (defaultFlowId == null || defaultFlowId.isBlank()) {
            return Optional.empty();
        }
        return outgoingFlows.stream()
                .filter(sf -> sf.id().equals(defaultFlowId))
                .findFirst();
    }
}
