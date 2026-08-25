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

import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.event.CriticalEventRecorder;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
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
    private final CriticalEventRecorder criticalEventRecorder;

    /**
     * Encapsula o contexto móvel e imutável de uma linha de execução (Branch).
     */
    public record ExecutionFrame(
            FlowNodeDefinition node,
            String nodeKey,
            String branchId,
            String joinTaskId
    ) {}

    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor, Navigator navigator, CriticalEventRecorder criticalEventRecorder) {
        this.flowNodeExecutor = flowNodeExecutor;
        this.navigator = navigator;
        this.criticalEventRecorder = criticalEventRecorder;
    }

    /**
     * Executa a agenda de nós em memória até atingir uma fronteira transacional,
     * um WaitState ou uma divisão paralela (Split).
     *
     * @param guardSynchronousHandlers quando {@code true}, força uma parada antes do primeiro
     *        {@code ExecutableTaskDefinition} encontrado nesta chamada, mesmo sem {@code commitBefore}
     *        declarado — usado exclusivamente pela saída de um boundary event interruptivo (timer ou catch
     *        event) disparando, para impedir que um handler com efeito colateral real rode antes do guard de
     *        finalização em {@code commitWork} decidir quem venceu a corrida contra um irmão ou o próprio pai
     *        concluindo normalmente (ver docs/engine/19-guard-de-finalizacao-boundary-events.md, seção "fluxo
     *        fantasma"). Nós de controle puro no caminho (gateways, join, end event) continuam avaliados
     *        normalmente — só o handler em si é adiado. {@code false} em todo o resto do motor.
     */
    public ExecutionResult executeFlow(
            FlowNodeDefinition startPoint,
            String startPointKey,
            String initialBranchId,
            String initialJoinTaskId,
            ProcessInstanceExecution processInstance,
            ProcessDefinition processDefinition,
            boolean isResumingFromAsyncBefore,
            boolean guardSynchronousHandlers) {

        Queue<ExecutionFrame> agenda = new LinkedList<>();
        agenda.add(new ExecutionFrame(startPoint, startPointKey, initialBranchId, initialJoinTaskId));

        List<OutboxEventEntity> criticalEvents = new ArrayList<>();
        boolean isFirstNodeInLoop = true;
        boolean branchConcluded = false;


        while (!agenda.isEmpty()) {
            ExecutionFrame currentFrame = agenda.poll();
            FlowNodeDefinition currentNode = currentFrame.node();
            String currentNodeKey = currentFrame.nodeKey();
            String currentBranchId = currentFrame.branchId();
            String currentJoinTaskId = currentFrame.joinTaskId();


            boolean mustGuardThisHandler = guardSynchronousHandlers && currentNode instanceof ExecutableTaskDefinition;
            final boolean shouldStopForCommitBefore = (isCommitBefore(currentNode) || mustGuardThisHandler)
                    && !(isFirstNodeInLoop && isResumingFromAsyncBefore);

            if (isWaitState(currentNode) || shouldStopForCommitBefore) {
                return new ExecutionResult(
                        new ExecutionOutcome(processInstance, criticalEvents),
                        new Continuation(List.of(currentNode), List.of(currentNodeKey), true)
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
            RuntimeException caughtException = null;

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
                    List<String> nextNodeKeys = handler.outgoing().stream()
                            .map(seq -> seq.targetNodeId())
                            .toList();

                    continuation = new Continuation(nextNodes, nextNodeKeys, Boolean.TRUE.equals(handler.commitAfter()));
                } else {

                    status = NodeExecutionStatus.ERROR;
                    caughtException = processError;
                }

            } catch (RuntimeException e) {
                status = NodeExecutionStatus.ERROR;
                caughtException = e;
            }

            if (criticalEventRecorder.isEnabled()) {
                final FlowNodeExecutionSnapshot snapshot = FlowNodeExecutionSnapshot.builder()
                        .flowNodeDefinition(currentNode)
                        .processDefinitionSnapshot(processDefinition)
                        .processInstanceSnapshot(ProcessInstanceMapper.mapToRecord(processInstance))
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .nodeExecutionStatus(status)
                        .build();

                criticalEventRecorder.recordFlowNodeFinished(criticalEvents, snapshot, caughtException);

                if (currentNode instanceof ExclusiveGatewayDefinition gateway && continuation != null) {
                    criticalEventRecorder.recordGatewayAnswerResolved(criticalEvents, processInstance, processDefinition, gateway, continuation);
                }
            }

            if (caughtException != null) {
                throw new FlowNodeExecutionFailure(caughtException, criticalEvents);
            }

            isFirstNodeInLoop = false;

            if (continuation == null || continuation.isAsynchronous()) {
                return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), continuation);
            } else {
                for (int i = 0; i < continuation.nextNodes().size(); i++) {
                    FlowNodeDefinition nextNode = continuation.nextNodes().get(i);
                    String nextNodeKey = continuation.nextNodeKeys().get(i);
                    agenda.add(new ExecutionFrame(nextNode, nextNodeKey, currentBranchId, currentJoinTaskId));
                }
            }
        }

        if (branchConcluded && agenda.isEmpty()) {
            return new ExecutionResult(
                    new ExecutionOutcome(processInstance, criticalEvents),
                    new io.kikwiflow.execution.dto.Continuation(List.of(), List.of(), true)
            );
        }

        return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), null);
    }

    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition) {
        return flowNodeDefinition instanceof WaitState;
    }

    /**
     * {@code TimerTaskDefinition} e {@code CallActivityDefinition} são sempre tratados como
     * {@code commitBefore: true}, independente do valor declarado no {@code .kikwi}. Um timer não pode, por
     * natureza, ser executado de forma síncrona (não há como "bloquear a thread" até o dueDate); um
     * {@code CALL_ACTIVITY_COORDINATOR} precisa de uma transação própria para materializar a coordenadora + N
     * iniciadoras (ver {@code ContinuationService.generateNextTasksWithContext}) — nunca pode rodar inline.
     * Ambos reaproveitam a mesma checagem de {@code isResumingFromAsyncBefore} já usada para
     * {@code ExecutableTaskDefinition} assíncrona, que corretamente distingue "primeira vez" (pausa) de
     * "retomando após o disparo" (segue para as arestas de saída) — ao contrário de {@code WaitState}, que
     * pausaria de novo indefinidamente numa {@code ExecutableTask} retomada. É essa distinção que faz a
     * retomada da própria coordenadora (após liberada de {@code AWAITING_BRANCHES}) cair direto no caminho
     * genérico de navegação, sem dispatch dedicado nenhum.
     */
    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore())
                || flowNodeDefinition instanceof TimerTaskDefinition
                || flowNodeDefinition instanceof CallActivityDefinition;
    }
}