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

package io.kikwiflow.view.adapter;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ManualTaskDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.view.model.manual.Workflow;
import io.kikwiflow.view.model.manual.WorkflowStage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class WorkflowAdapter {

    public static Workflow toManualWorkflow(ProcessDefinition definition) {
        List<WorkflowStage> stages = new ArrayList<>();
        Map<String, FlowNodeDefinition> nodes = definition.flowNodes();

        findNextHumanTask(definition.defaultStartPoint(), nodes)
                .ifPresent(firstTask -> {
                    ManualTaskDefinition currentTask = firstTask;
                    while (currentTask != null) {
                        Optional<ManualTaskDefinition> nextTask = findNextHumanTask(currentTask, nodes);
                        stages.add(new WorkflowStage(
                                currentTask.id(),
                                currentTask.name(),
                                nextTask.map(FlowNodeDefinition::id).map(Collections::singletonList).orElse(null),
                                currentTask.extensionProperties()
                        ));
                        currentTask = nextTask.orElse(null);
                    }
                });

        return new Workflow(
                definition.id(),
                definition.key(),
                definition.name(),
                null, //TODO
                stages
        );
    }

    /**
     * Navega pelo grafo do processo a partir de um nó inicial para encontrar a próxima tarefa humana.
     * Ele ignora outros tipos de nós (ServiceTasks, Gateways, etc.) no caminho.
     */
    private static Optional<ManualTaskDefinition> findNextHumanTask(FlowNodeDefinition startNode, Map<String, FlowNodeDefinition> allNodes) {
        FlowNodeDefinition currentNode = startNode;
        Set<String> visited = new HashSet<>();

        while (currentNode != null) {
            if (!visited.add(currentNode.id())) {
                return Optional.empty();
            }

            String nextNodeId = getNextNodeId(currentNode);
            if (nextNodeId == null) {
                return Optional.empty();
            }

            currentNode = allNodes.get(nextNodeId);

            if (currentNode instanceof ManualTaskDefinition) {
                return Optional.of((ManualTaskDefinition) currentNode);
            }
        }
        return Optional.empty();
    }

    // Lógica simplificada de navegação: segue o fluxo padrão de gateways ou o primeiro fluxo.
    // TODO: Tornar esta lógica mais robusta para lidar com gateways complexos.
    private static String getNextNodeId(FlowNodeDefinition node) {
        if (node.outgoing().isEmpty()) return null;
        if (node instanceof ExclusiveGatewayDefinition gw && gw.outgoing() != null) {
            return gw.outgoing().stream()
                    .filter(flow -> Objects.isNull(flow.condition()))
                    .findFirst()
                    .map(SequenceFlowDefinition::targetNodeId)
                    .orElse(node.outgoing().get(0).targetNodeId());
        }

        return node.outgoing().get(0).targetNodeId();
    }
}
