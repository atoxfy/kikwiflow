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
package io.kikwiflow.management.history;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.persistence.api.history.HistoricInstanceSummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta o {@link InstanceTrace} a partir da linha do tempo já ordenada e mascarada. Função pura: não lê banco.
 * <p>
 * De onde vem cada "passagem" por um nó: nós executados pelo motor emitem {@code FLOW_NODE_FINISHED}; tarefas
 * externas e esperas por evento só emitem {@code EXTERNAL_TASK_COMPLETED}; um nó interrompido por um boundary
 * event emite {@code FLOW_NODE_FINISHED} com status {@code INTERRUPTED} e aponta quem o interrompeu.
 */
public final class InstanceTraceAssembler {

    private InstanceTraceAssembler() {}

    /**
     * @param events         histórico ordenado e mascarado da instância.
     * @param definition     versão da definição que a instância executou, para nomear gateways e tarefas; pode
     *                       ser {@code null} (os nomes ficam vazios).
     * @param activeNodes    onde a instância está agora; vazio se ela já terminou.
     * @param liveVariables  variáveis atuais (já mascaradas) de uma instância ainda ativa; ignoradas se houver
     *                       evento de fim.
     */
    public static InstanceTrace assemble(List<HistoryEventSummary> events, ProcessDefinition definition,
                                         Map<String, Integer> activeNodes, Map<String, ProcessVariable> liveVariables) {
        ProcessInstanceStarted started = null;
        ProcessInstanceFinished finished = null;
        Map<String, Integer> visited = new LinkedHashMap<>();
        Map<String, Boolean> lastPassInterrupted = new HashMap<>();
        Set<String> interruptors = new LinkedHashSet<>();
        List<String> takenFlowIds = new ArrayList<>();
        List<TraceDecision> decisions = new ArrayList<>();
        List<TraceHumanAction> humanActions = new ArrayList<>();

        for (HistoryEventSummary event : events) {
            switch (event.payload()) {
                case ProcessInstanceStarted s -> started = s;
                case ProcessInstanceFinished f -> finished = f;
                case FlowNodeFinished node -> {
                    NodeExecutionStatus status = node.getNodeExecutionStatus();
                    if (status == NodeExecutionStatus.ERROR) {
                        break; // tentativa que falhou: aparece na linha do tempo, não conta como passagem
                    }
                    visited.merge(node.getFlowNodeDefinitionId(), 1, Integer::sum);
                    boolean interrupted = status == NodeExecutionStatus.INTERRUPTED;
                    lastPassInterrupted.put(node.getFlowNodeDefinitionId(), interrupted);
                    if (interrupted && node.getInterruptedByNodeDefinitionId() != null) {
                        interruptors.add(node.getInterruptedByNodeDefinitionId());
                    }
                }
                case ExternalTaskCompleted completed -> {
                    visited.merge(completed.taskDefinitionId(), 1, Integer::sum);
                    lastPassInterrupted.put(completed.taskDefinitionId(), false);
                    humanActions.add(new TraceHumanAction(completed.taskDefinitionId(),
                            nodeName(definition, completed.taskDefinitionId()), completed.actorId(), completed.assignee(),
                            completed.completedAt(), completed.submittedVariables()));
                }
                case GatewayAnswerResolved answer -> {
                    if (answer.chosenFlowId() != null) {
                        takenFlowIds.add(answer.chosenFlowId());
                    }
                    decisions.add(new TraceDecision(answer.gatewayNodeId(), nodeName(definition, answer.gatewayNodeId()),
                            answer.resolvedAnswer(), answer.chosenFlowId(),
                            answer.answerProviderType() != null ? answer.answerProviderType().name() : null,
                            answer.providerBean() != null ? answer.providerBean() : answer.providerVariable(),
                            answer.evaluatedAt()));
                }
                default -> { }
            }
        }

        // O timer que interrompeu um nó também foi "percorrido", mesmo quando não emite evento próprio.
        interruptors.forEach(timerId -> visited.putIfAbsent(timerId, 1));

        Set<String> interruptedNodes = new LinkedHashSet<>();
        lastPassInterrupted.forEach((nodeId, interrupted) -> {
            if (interrupted) interruptedNodes.add(nodeId);
        });

        Map<String, Integer> currentlyActive = new LinkedHashMap<>();
        if (activeNodes != null) {
            activeNodes.forEach((nodeId, tokens) -> {
                if (tokens != null && tokens > 0) currentlyActive.put(nodeId, tokens);
            });
        }

        return new InstanceTrace(
                started != null ? HistoricInstanceSummary.of(started, finished) : null,
                currentlyActive,
                events,
                visited,
                interruptedNodes,
                takenFlowIds,
                decisions,
                humanActions,
                finished != null && finished.getVariables() != null ? finished.getVariables()
                        : liveVariables != null ? liveVariables : Map.of()
        );
    }

    private static String nodeName(ProcessDefinition definition, String nodeId) {
        if (definition == null || nodeId == null) return null;
        FlowNodeDefinition node = definition.flowNodes().get(nodeId);
        return node != null ? node.name() : null;
    }
}
