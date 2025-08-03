/*
 * Copyright Atoxfy and/or licensed to Atoxfy
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

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.bpmn.elements.SequenceFlow;
import io.kikwiflow.model.execution.Continuation;
import io.kikwiflow.model.execution.ProcessInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates next node based on a execution graph
 * @author Emiliano Fagundes
 */
public class Navigator {

    private final ProcessDefinitionManager processDefinitionManager;

    public Navigator(ProcessDefinitionManager processDefinitionManager) {
        this.processDefinitionManager = processDefinitionManager;
    }

    public FlowNode findStartPoint(ProcessDefinition processDefinition){
        return processDefinition.getDefaultStartPoint();
    }

    public Continuation determineNextContinuation(FlowNode completedNode, ProcessDefinition processDefinition, boolean forceAsync) {

        List<SequenceFlow> outgoingFlows = completedNode.getOutgoing();

        if (outgoingFlows.isEmpty()) {
            // É um EndEvent ou um nó sem saída, o processo termina aqui.
            return null;
        }

        List<FlowNode> nextNodes = new ArrayList<>();

        /*
        // Aqui futuramente adicionar logica por tipo de node
        if (completedNode instanceof ExclusiveGatewayNode) {
            // encontrar o primeiro caminho cuja condição é verdadeira.
            for (SequenceFlow flow : outgoingFlows) {
                if (flow.getConditionExpression() == null) {
                    nextNodes.add(definition.getFlowNodes().get(flow.getTargetNodeId()));
                    break;
                }
                if (evaluateExpression(flow.getConditionExpression(), instance.getVariables())) {
                    nextNodes.add(definition.getFlowNodes().get(flow.getTargetNodeId()));
                    break;
                }
            }
        } else {*/
        // }

        //para nodos de uma saida só
        String targetNodeId = outgoingFlows.get(0).getTargetNodeId();
        nextNodes.add(processDefinition.getFlowNodes().get(targetNodeId));

        /*

        if (nextNodes.isEmpty()) {
            // Nenhum caminho foi satisfeito no gateway.
            throw new RuntimeException("Nenhum caminho de saída válido encontrado para o nó " + completedNode.getId());
        } */

        // Agora, determinar se a continuação é síncrona ou assíncrona.
        boolean isAsync = false;
        if (forceAsync) {
            // O nó anterior tinha um "commit-after", forçando a próxima etapa a ser assíncrona.
            isAsync = true;
        } else {
            // Verificamos se o *próximo* nó pede para ser assíncrono.
            // (Simplificado para um fluxo linear, o primeiro nó da lista decide)
            isAsync = nextNodes.get(0).getCommitBefore();
        }

        return new Continuation(nextNodes, isAsync);
    }

    public Continuation determineNextContinuation(FlowNode completedNode, ProcessInstance instance, boolean forceAsync) {

        ProcessDefinition definition = processDefinitionManager.getByKey(instance.getProcessDefinitionId()).get();//todo
        return determineNextContinuation(completedNode, instance, forceAsync);

    }
}
