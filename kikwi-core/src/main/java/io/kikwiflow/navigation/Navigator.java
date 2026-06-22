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

import io.kikwiflow.decision.api.AnswerProvider;
import io.kikwiflow.decision.api.AnswerProviderLocator;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Navigator {

    private final AnswerProviderLocator answerProviderLocator;

    public Navigator(AnswerProviderLocator answerProviderLocator) {
        this.answerProviderLocator = answerProviderLocator;
    }

    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessDefinition processDefinition, Map<String, ProcessVariable> variables, boolean forceAsync) {
        List<SequenceFlowDefinition> outgoingFlows = completedNode.outgoing();
        if ("PARALLEL_GATEWAY".equals(completedNode.type())) {
            List<FlowNodeDefinition> nextNodes = new ArrayList<>();
            for (SequenceFlowDefinition flow : outgoingFlows) {
                FlowNodeDefinition target = processDefinition.flowNodes().get(flow.targetNodeId());
                if (target != null) {
                    nextNodes.add(target);
                }
            }

            ParallelGatewayDefinition parallelGateway = (ParallelGatewayDefinition) completedNode;
            String targetJoinId = parallelGateway.targetJoinId();

            if (targetJoinId == null) {
                throw new RuntimeException("Não foi encontrado join ");
            }

            FlowNodeDefinition targetJoinNode = processDefinition.flowNodes().get(targetJoinId);

            return new Continuation(nextNodes, true, null, null, targetJoinNode);
        }

        if ("JOIN_GATEWAY".equals(completedNode.type())) {
            if (outgoingFlows.isEmpty()) {
                return null;
            }

            SequenceFlowDefinition defaultFlow = outgoingFlows.get(0);
            FlowNodeDefinition nextNode = processDefinition.flowNodes().get(defaultFlow.targetNodeId());
            return new Continuation(List.of(nextNode), forceAsync, null, defaultFlow.id(), null);
        }

        if (outgoingFlows.isEmpty()) {
            return null;
        }

        List<FlowNodeDefinition> nextNodes = new ArrayList<>();
        String recordedAnswer = null;
        String recordedFlowId = null;

        if (completedNode instanceof ExclusiveGatewayDefinition gateway) {
            recordedAnswer = resolveAnswer(gateway, variables);
            SequenceFlowDefinition chosenFlow = findMatchingFlow(gateway, recordedAnswer);
            recordedFlowId = chosenFlow.id();

            FlowNodeDefinition nextNode = processDefinition.flowNodes().get(chosenFlow.targetNodeId());
            if (nextNode == null) {
                throw new IllegalStateException("Architectural Error: Target node '" + chosenFlow.targetNodeId() +
                        "' defined in sequence flow does not exist in the process definition.");
            }
            nextNodes.add(nextNode);

        } else {
            SequenceFlowDefinition defaultFlow = outgoingFlows.get(0);
            recordedFlowId = defaultFlow.id();
            FlowNodeDefinition nextNode = processDefinition.flowNodes().get(defaultFlow.targetNodeId());
            nextNodes.add(nextNode);
        }

        boolean isAsync = forceAsync || (nextNodes.get(0) != null && Boolean.TRUE.equals(nextNodes.get(0).commitBefore()));

        return new Continuation(nextNodes, isAsync, recordedAnswer, recordedFlowId, null);
    }

    private String resolveAnswer(ExclusiveGatewayDefinition gateway, Map<String, ProcessVariable> variables) {
        if (gateway.providerType() == AnswerProviderType.VARIABLE) {
            if (gateway.providerVariable() == null || gateway.providerVariable().isBlank()) {
                throw new IllegalStateException("Architectural Error: Gateway '" + gateway.id() + "' está configurado como VARIABLE, mas 'providerVariable' é nulo ou vazio.");
            }
            ProcessVariable variable = variables.get(gateway.providerVariable());
            return variable != null && variable.value() != null ? variable.value().toString() : null;
        }
        if (gateway.providerType() == AnswerProviderType.BEAN) {
            if (gateway.providerBean() == null || gateway.providerBean().isBlank()) {
                throw new IllegalStateException("Architectural Error: Gateway '" + gateway.id() + "' está configurado como BEAN, mas 'providerBean' é nulo ou vazio.");
            }
            AnswerProvider provider = answerProviderLocator.getProvider(gateway.providerBean());
            if (provider == null) {
                throw new IllegalStateException("Execution Error: Nenhum AnswerProvider encontrado para o bean '" + gateway.providerBean() + "'.");
            }
            return provider.resolve(new MapAnswerContextAdapter(gateway.id(), variables));
        }
        throw new IllegalStateException("Execution Error: Tipo de AnswerProvider não suportado ou nulo no gateway '" + gateway.id() + "'.");
    }

    private SequenceFlowDefinition findMatchingFlow(ExclusiveGatewayDefinition gateway, String answer) {
        List<SequenceFlowDefinition> outgoingFlows = gateway.outgoing();
        if (answer == null) {
            return outgoingFlows.stream()
                    .filter(SequenceFlowDefinition::handlesNull)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Execution Error: A resposta do gateway '" + gateway.id() + "' foi nula, mas não existe nenhuma aresta configurada com 'handlesNull'."));
        }
        Optional<SequenceFlowDefinition> matchedFlow = outgoingFlows.stream()
                .filter(sf -> answer.equals(sf.expectedAnswer()))
                .findFirst();
        if (matchedFlow.isPresent()) {
            return matchedFlow.get();
        }
        return outgoingFlows.stream()
                .filter(SequenceFlowDefinition::isDefault)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Execution Error: Nenhuma aresta corresponde à resposta '" + answer + "' no gateway '" + gateway.id() + "' e nenhuma aresta 'isDefault' foi configurada."));
    }
}