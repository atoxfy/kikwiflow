/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kikwiflow.execution;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.navigation.Navigator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Orquestra a execução de um fluxo de processo baseado em uma Agenda de Contexto Isolado.
 * <p>
 * Substitui o loop linear por uma fila de {@link ExecutionFrame}s, permitindo a propagação
 * nativa de escopos de ramificação (branchId) e alvos de confluência (joinTaskId) sem gerar
 * condições de corrida em memória ou travas complexas de concorrência.
 */
public class ProcessExecutionManager {

    private final FlowNodeExecutor flowNodeExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;

    /**
     * Encapsula o contexto móvel e imutável de uma linha de execução (Branch).
     */
    public record ExecutionFrame(
            FlowNodeDefinition node,
            String branchId,
            String joinTaskId
    ) {}

    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig) {
        this.flowNodeExecutor = flowNodeExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    /**
     * Executa a agenda de nós em memória até atingir uma fronteira transacional,
     * um WaitState ou uma divisão paralela (Split).
     */
    public ExecutionResult executeFlow(
            FlowNodeDefinition startPoint,
            String initialBranchId,
            String initialJoinTaskId,
            ProcessInstanceExecution processInstance,
            ProcessDefinition processDefinition,
            boolean isResumingFromAsyncBefore) {

        Queue<ExecutionFrame> agenda = new LinkedList<>();
        agenda.add(new ExecutionFrame(startPoint, initialBranchId, initialJoinTaskId));

        List<OutboxEventEntity> criticalEvents = new ArrayList<>();
        boolean isFirstNodeInLoop = true;
        boolean branchConcluded = false;


        while (!agenda.isEmpty()) {
            ExecutionFrame currentFrame = agenda.poll();
            FlowNodeDefinition currentNode = currentFrame.node();
            String currentBranchId = currentFrame.branchId();
            String currentJoinTaskId = currentFrame.joinTaskId();


            final boolean shouldStopForCommitBefore = isCommitBefore(currentNode) && !(isFirstNodeInLoop && isResumingFromAsyncBefore);

            if (isWaitState(currentNode) || shouldStopForCommitBefore) {
                return new ExecutionResult(
                        new ExecutionOutcome(processInstance, criticalEvents),
                        new Continuation(List.of(currentNode), true)
                );
            }

            boolean isLegitimateJoinResumption = "JOIN_GATEWAY".equals(currentNode.type()) && isFirstNodeInLoop && isResumingFromAsyncBefore;

            if (("DEFAULT_END_EVENT".equals(currentNode.type()) || "JOIN_GATEWAY".equals(currentNode.type()))
                    && !isLegitimateJoinResumption
                    && currentBranchId != null) {

                if (currentJoinTaskId != null) {
                    processInstance.registerBranchConclusion(currentJoinTaskId, currentBranchId);
                }
                branchConcluded = true;
                continue;
            }

            Instant startedAt = Instant.now();
            NodeExecutionStatus status = NodeExecutionStatus.SUCCESS;
            Continuation continuation = null;
            Exception caughtException = null;

            try {
                flowNodeExecutor.execute(processInstance, processDefinition, currentNode);
                boolean isCommitAfter = Boolean.TRUE.equals(currentNode.commitAfter());

                continuation = navigator.determineNextContinuation(currentNode, processDefinition, processInstance.getVariables(), isCommitAfter);

            } catch (ProcessErrorException processError) {

                Optional<ErrorHandlerDefinition> boundary = navigator.findMatchingErrorHandler(currentNode, processDefinition, processError.getErrorCode());

                if (boundary.isPresent()) {
                    status = NodeExecutionStatus.INTERRUPTED;
                    ErrorHandlerDefinition handler = boundary.get();

                    List<FlowNodeDefinition> nextNodes = handler.outgoing().stream()
                            .map(seq -> processDefinition.flowNodes().get(seq.targetNodeId()))
                            .toList();

                    continuation = new Continuation(nextNodes, Boolean.TRUE.equals(handler.commitAfter()));
                } else {

                    status = NodeExecutionStatus.ERROR;
                    caughtException = processError;
                }

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

                FlowNodeFinished flowNodeFinished = FlowNodeFinished.builder()
                        .flowNodeDefinitionId(snapshot.flowNodeDefinition().id())
                        .flowNodeType(snapshot.flowNodeDefinition().type())
                        .processInstanceId(snapshot.processInstance().id())
                        .processDefinitionId(snapshot.processDefinition().id())
                        .nodeExecutionStatus(snapshot.nodeExecutionStatus())
                        .startedAt(snapshot.startedAt())
                        .finishedAt(snapshot.finishedAt())
                        .build();

                criticalEvents.add(new OutboxEventEntity("FLOW_NODE_FINISHED", flowNodeFinished));

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
                for (FlowNodeDefinition nextNode : continuation.nextNodes()) {
                    agenda.add(new ExecutionFrame(nextNode, currentBranchId, currentJoinTaskId));
                }
            }
        }

        if (branchConcluded && agenda.isEmpty()) {
            return new ExecutionResult(
                    new ExecutionOutcome(processInstance, criticalEvents),
                    new io.kikwiflow.execution.dto.Continuation(List.of(), true)
            );
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