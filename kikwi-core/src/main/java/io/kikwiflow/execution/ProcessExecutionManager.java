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
package io.kikwiflow.execution;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.event.FlowNodeExecuted;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.navigation.Navigator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra a execução síncrona de um fluxo de processo.
 * <p>
 * Esta classe contém o loop de execução principal do motor. A sua responsabilidade é
 * receber um ponto de partida e conduzir a instância do processo através dos nós
 * sequenciais, utilizando o {@link Navigator} para determinar o próximo passo e o
 * {@link FlowNodeExecutor} para executar a lógica de cada nó.
 * <p>
 * A execução continua até que uma das seguintes condições de paragem seja encontrada:
 * <ul>
 *     <li>Um nó que é um estado de espera ({@link WaitState}).</li>
 *     <li>Um nó que exige um commit transacional antes da sua execução (commit-before).</li>
 *     <li>O fim do fluxo do processo (nenhum nó de saída).</li>
 * </ul>
 */

public class ProcessExecutionManager {

    private final FlowNodeExecutor flowNodeExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;

    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig) {
        this.flowNodeExecutor = flowNodeExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    public ExecutionResult executeFlow(FlowNodeDefinition startPoint, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition, boolean isResumingFromAsyncBefore) {
        FlowNodeDefinition currentNode = startPoint;
        List<OutboxEventEntity> criticalEvents = new ArrayList<>();
        boolean isFirstNodeInLoop = true;

        while (currentNode != null) {

            final boolean shouldStopForCommitBefore = isCommitBefore(currentNode) && !(isFirstNodeInLoop && isResumingFromAsyncBefore);
            if (isWaitState(currentNode) || shouldStopForCommitBefore) {
                return new ExecutionResult(
                        new ExecutionOutcome(processInstance, criticalEvents),
                        new Continuation(List.of(currentNode), true)
                );
            }

            Instant startedAt = Instant.now();
            NodeExecutionStatus status = NodeExecutionStatus.SUCCESS;
            Continuation continuation = null;
            Exception caughtException = null;

            try {

                flowNodeExecutor.execute(processInstance, processDefinition, currentNode);
                boolean isCommitAfter = Boolean.TRUE.equals(currentNode.commitAfter());
                continuation = navigator.determineNextContinuation(currentNode, processDefinition, processInstance.getVariables(), isCommitAfter);

            } catch (Exception e) {
                status = NodeExecutionStatus.ERROR;
                caughtException = e;
            }

            if (kikwiflowConfig.isStatsEnabled() || kikwiflowConfig.isOutboxEventsEnabled()) {
                final FlowNodeExecutionSnapshot snapshot = FlowNodeExecutionSnapshot.builder()
                        .flowNodeDefinition(currentNode)
                        .processDefinitionSnapshot(processDefinition)
                        .processInstanceSnapshot(ProcessInstanceMapper.mapToRecord(processInstance))
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .nodeExecutionStatus(status)
                        .build();

                FlowNodeExecuted flowNodeExecuted = FlowNodeExecuted.builder()
                        .flowNodeDefinitionId(snapshot.flowNodeDefinition().id())
                        .processInstanceId(snapshot.processInstance().id())
                        .processDefinitionId(snapshot.processDefinition().id())
                        .nodeExecutionStatus(snapshot.nodeExecutionStatus())
                        .startedAt(snapshot.startedAt())
                        .finishedAt(snapshot.finishedAt())
                        .build();

                criticalEvents.add(new OutboxEventEntity("FLOW_NODE_EXECUTED", flowNodeExecuted));

                if (currentNode instanceof ExclusiveGatewayDefinition && continuation != null) {
                    GatewayAnswerResolved answerEvent = new GatewayAnswerResolved(
                            processInstance.getId(),
                            processDefinition.id(),
                            currentNode.id(),
                            ((ExclusiveGatewayDefinition) currentNode).providerType(),
                            ((ExclusiveGatewayDefinition) currentNode).providerBean(),
                            ((ExclusiveGatewayDefinition) currentNode).providerVariable(),
                            continuation.resolvedAnswer(),
                            continuation.chosenFlowId(),
                            Instant.now()
                    );

                    criticalEvents.add(new OutboxEventEntity("GATEWAY_ANSWER_RESOLVED", answerEvent));
                }
            }

            if (caughtException != null) {
                throw new RuntimeException("Execution Error: Falha na execução ou roteamento do nó [" + currentNode.id() + "]", caughtException);
            }

            isFirstNodeInLoop = false;
            if (continuation == null || continuation.isAsynchronous()) {
                return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), continuation);
            } else {
                currentNode = continuation.nextNodes().get(0);
            }
        }

        return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), null);
    }

    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition) {
        return flowNodeDefinition instanceof WaitState;
    }

    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore());
    }
}