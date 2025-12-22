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
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.view.model.manual.Workflow;
import io.kikwiflow.view.model.manual.WorkflowStage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WorkflowAdapter {

    public static Workflow toManualWorkflow(ProcessDefinition definition) {
        Map<String, WorkflowStage> stagesMap = new LinkedHashMap<>();
        buildStagesRecursively(definition.defaultStartPoint(), definition.flowNodes(), stagesMap, new HashSet<>());

        return new Workflow(
                definition.id(),
                definition.key(),
                definition.name(),
                definition.description(),
                new ArrayList<>(stagesMap.values())
        );
    }

    /**
     * Constrói recursivamente a lista de estágios (tarefas humanas) navegando pelo grafo do processo.
     * Ele popula um mapa de estágios para evitar o processamento da mesma tarefa várias vezes.
     */
    private static void buildStagesRecursively(
            FlowNodeDefinition currentNode,
            Map<String, FlowNodeDefinition> allNodes,
            Map<String, WorkflowStage> stages,
            Set<String> visitedNodes) {

        if (currentNode == null || !visitedNodes.add(currentNode.id())) {
            return;
        }

        if (currentNode instanceof ExternalTaskDefinition manualTask) {
            List<ExternalTaskDefinition> nextHumanTasks = findNextHumanTasks(manualTask, allNodes, new HashSet<>());

            stages.put(manualTask.id(), new WorkflowStage(
                    manualTask.id(),
                    manualTask.name(),
                    nextHumanTasks.stream().map(FlowNodeDefinition::id).collect(Collectors.toList()),
                    manualTask.extensionProperties()
            ));

            for (ExternalTaskDefinition nextTask : nextHumanTasks) {
                buildStagesRecursively(nextTask, allNodes, stages, visitedNodes);
            }
        } else {
            currentNode.outgoing().forEach(flow -> {
                FlowNodeDefinition nextNode = allNodes.get(flow.targetNodeId());
                buildStagesRecursively(nextNode, allNodes, stages, visitedNodes);
            });
        }
    }

    /**
     * A partir de um nó inicial, encontra a(s) próxima(s) tarefa(s) humana(s) no fluxo,
     * atravessando nós não-humanos (como gateways e tarefas de sistema).
     */
    private static List<ExternalTaskDefinition> findNextHumanTasks(
            FlowNodeDefinition startNode,
            Map<String, FlowNodeDefinition> allNodes,
            Set<String> visitedNodesInPath) {

        List<ExternalTaskDefinition> foundTasks = new ArrayList<>();
        if (startNode == null || !visitedNodesInPath.add(startNode.id())) {
            return foundTasks;
        }

        for (var flow : startNode.outgoing()) {
            FlowNodeDefinition nextNode = allNodes.get(flow.targetNodeId());

            if (nextNode instanceof ExternalTaskDefinition manualTask) {
                foundTasks.add(manualTask);
            } else {
                foundTasks.addAll(findNextHumanTasks(nextNode, allNodes, visitedNodesInPath));
            }
        }
        return foundTasks;
    }
}
