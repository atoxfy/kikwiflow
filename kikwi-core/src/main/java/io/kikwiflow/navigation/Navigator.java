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

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.ProcessInstanceExecution;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsável pela lógica de navegação dentro do grafo de um processo BPMN.
 * <p>
 * Esta classe encapsula as regras para determinar qual o próximo nó a ser executado,
 * com base no estado atual do processo e na estrutura da definição do processo.
 * Ela lida com a travessia dos fluxos de sequência (sequence flows) e a identificação
 * de pontos de início e continuação.
 */
public class Navigator {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * Constrói uma nova instância do Navigator.
     *
     * @param processDefinitionService O gestor de definições de processo, usado para obter
     *                                 informações sobre o grafo do processo quando necessário.
     */
    public Navigator(ProcessDefinitionService processDefinitionService) {
        this.processDefinitionService = processDefinitionService;
    }

    /**
     * Encontra o ponto de início padrão para uma dada definição de processo.
     *
     * @param processDefinition A definição do processo.
     * @return O {@link FlowNodeDefinition} que representa o evento de início padrão.
     */
    public FlowNodeDefinition findStartPoint(ProcessDefinition processDefinition){
        return processDefinition.defaultStartPoint();
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
    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessDefinition processDefinition, boolean forceAsync) {

        List<SequenceFlowDefinition> outgoingFlows = completedNode.outgoing();

        if (outgoingFlows.isEmpty()) {
            // É um EndEvent ou um nó sem saída, o processo termina aqui.
            return null;
        }

        List<FlowNodeDefinition> nextNodes = new ArrayList<>();

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
        String targetNodeId = outgoingFlows.get(0).targetNodeId();
        nextNodes.add(processDefinition.flowNodes().get(targetNodeId));

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
            isAsync = Boolean.TRUE.equals(nextNodes.get(0).commitBefore());
        }

        return new Continuation(nextNodes, isAsync);
    }

    /**
     * Método de conveniência para determinar a próxima continuação a partir de uma instância de processo.
     * <p>
     * <strong>Nota:</strong> Este método busca a definição do processo a cada chamada, o que pode
     * introduzir sobrecarga. Prefira a versão que recebe a {@link ProcessDefinition} diretamente.
     *
     * @param completedNode O nó que acabou de ser executado.
     * @param instance A instância de processo em execução.
     * @param forceAsync Se a continuação deve ser forçada a ser assíncrona.
     * @return Um objeto {@link Continuation} ou {@code null}.
     */
    public Continuation determineNextContinuation(FlowNodeDefinition completedNode, ProcessInstanceExecution instance, boolean forceAsync) {

        ProcessDefinition definition = processDefinitionService.getByKey(instance.getProcessDefinitionId()).get();//todo
        return determineNextContinuation(completedNode, definition, forceAsync);
    }
}
