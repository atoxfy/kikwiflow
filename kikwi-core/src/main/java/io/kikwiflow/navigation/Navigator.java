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

import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.execution.DecisionRuleResolver;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.rule.api.DecisionRule;

import javax.swing.*;
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

    /**
     * Determina a próxima continuação (próximos passos) após a conclusão de um nó.
     * <p>
     * Atualmente, suporta apenas um fluxo de saída linear. Lógicas para gateways
     * (paralelo, exclusivo) serão adicionadas no futuro.
     *
     * @param completedNode O nó que acabou de ser executado.
     * @param processDefinition A definição do processo à qual o nó pertence.
     * @param forceAsync Se a continuação deve ser forçada a ser assíncrona (por exemplo, após um commit).
     * @return Um objeto {@link Continuation} descrevendo os próximos nós e se a execução
     *         deve ser síncrona ou assíncrona. Retorna {@code null} se for um nó final.
     */
    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessDefinition processDefinition, Map<String, ProcessVariable> variables, boolean forceAsync) {

        List<SequenceFlowDefinition> outgoingFlows = completedNode.outgoing();

        if (outgoingFlows.isEmpty()) {
            // É um EndEvent ou um nó sem saída, o processo termina aqui.
            return null;
        }

        List<FlowNodeDefinition> nextNodes = new ArrayList<>();

        // Aqui futuramente adicionar logica por tipo de node
        if (completedNode instanceof ExclusiveGatewayDefinition) {

            FlowNodeDefinition defaultNextNode = null;
            // encontrar o primeiro caminho cuja condição é verdadeira.
            for (SequenceFlowDefinition flow : outgoingFlows) {
                if (flow.condition() == null || flow.condition().isEmpty() ) {
                   //É saida de um gateway e não possui condição
                    if(defaultNextNode == null){
                        defaultNextNode = processDefinition.flowNodes().get(flow.targetNodeId());
                    }
                    continue;
                }

                DecisionRule decisionRule = decisionRuleResolver.resolve(flow.condition()).orElseThrow();
                if (decisionRule.evaluate(variables)) {
                    nextNodes.add(processDefinition.flowNodes().get(flow.targetNodeId()));
                    break;
                }
            }

            if(nextNodes.isEmpty() && defaultNextNode != null){
                nextNodes.add(defaultNextNode);
            }

            if(nextNodes.isEmpty()){
                throw new RuntimeException("Execution Error: cannot determine sequence flow");
            }

        } else {
            //para nodos de uma saida só
            String targetNodeId = outgoingFlows.get(0).targetNodeId();
            nextNodes.add(processDefinition.flowNodes().get(targetNodeId));
        }

        // Agora, determinar se a continuação é síncrona ou assíncrona.
        boolean isAsync = false;
        if (forceAsync) {
            // O nó anterior tinha um "commit-after", forçando a próxima etapa a ser assíncrona.
            isAsync = true;
        } else {
            // Verificamos se o *próximo* nó pede para ser assíncrono.
            // (Simplificado para um fluxo linear, o primeiro nó da lista decide)
            isAsync = Boolean.TRUE.equals(nextNodes.get(0).commitBefore());
        }

        return new Continuation(nextNodes, isAsync);
    }
}
