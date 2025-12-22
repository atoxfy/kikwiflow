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
package io.kikwiflow.management.rest.processDefinitions;

import io.kikwiflow.management.rest.processDefinitions.dto.ProcessStagesView;
import io.kikwiflow.management.rest.processDefinitions.dto.Stage;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.navigation.ProcessDefinitionService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcessDefinitionViewService {

    private final ProcessDefinitionService processDefinitionService;

    public ProcessDefinitionViewService(ProcessDefinitionService processDefinitionService) {
        this.processDefinitionService = processDefinitionService;
    }

    public Optional<ProcessStagesView> getHumanTaskStages(String processDefinitionKey) {
        return processDefinitionService.getByKey(processDefinitionKey)
            .map(this::transformToStagesView);
    }

    private ProcessStagesView transformToStagesView(ProcessDefinition definition) {
        List<Stage> stages = new ArrayList<>();
        Map<String, FlowNodeDefinition> nodes = definition.flowNodes();

        // 1. Encontra a primeira tarefa humana, começando do início do processo.
        findNextHumanTask(definition.defaultStartPoint(), nodes)
            .ifPresent(firstTask -> {
                ExternalTaskDefinition currentTask = firstTask;
                while (currentTask != null) {
                    // 2. Para a tarefa atual, encontra a próxima tarefa humana no fluxo.
                    Optional<ExternalTaskDefinition> nextTask = findNextHumanTask(currentTask, nodes);

                    stages.add(new Stage(
                        currentTask.id(),
                        currentTask.name(),
                        "128, 128, 128", // Cor de exemplo, pode ser configurada no futuro
                        nextTask.map(FlowNodeDefinition::id).orElse(null)
                    ));

                    // 3. Avança para a próxima tarefa para a próxima iteração.
                    currentTask = nextTask.orElse(null);
                }
            });

        return new ProcessStagesView(
            definition.id(),
            definition.key(),
            definition.name(),
            null,
            stages
        );
    }

    /**
     * Navega pelo grafo do processo a partir de um nó inicial para encontrar a próxima tarefa humana.
     * Ele ignora outros tipos de nós (ServiceTasks, Gateways, etc.) no caminho.
     */
    private Optional<ExternalTaskDefinition> findNextHumanTask(FlowNodeDefinition startNode, Map<String, FlowNodeDefinition> allNodes) {
        FlowNodeDefinition currentNode = startNode;
        Set<String> visited = new HashSet<>();

        while (currentNode != null) {
            if (!visited.add(currentNode.id())) {
                return Optional.empty(); // Ciclo detectado, paramos a busca.
            }

            // Obtém o próximo nó na sequência.
            String nextNodeId =  null;
            if (nextNodeId == null) {
                return Optional.empty(); // Fim do fluxo.
            }

            currentNode = allNodes.get(nextNodeId);

            // Se encontramos uma tarefa humana, retornamos.
            if (currentNode instanceof ExternalTaskDefinition) {
                return Optional.of((ExternalTaskDefinition) currentNode);
            }
        }
        return Optional.empty();
    }
}