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
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.event.CriticalEventRecorder;
import io.kikwiflow.execution.evaluator.CorrelationKeyResolver;
import io.kikwiflow.execution.evaluator.TimerDueDateEvaluator;
import io.kikwiflow.execution.api.dto.CorrelationItem;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.CallActivityIterationMode;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.AttachedEventReference;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContinuationService {

    private final KikwiEngineRepository kikwiEngineRepository;
    private final TimerDueDateEvaluator timerDueDateEvaluator;
    private final CorrelationKeyResolver correlationKeyResolver;
    private final KikwiflowConfig kikwiflowConfig;
    private final CriticalEventRecorder criticalEventRecorder;

    public ContinuationService(KikwiEngineRepository kikwiEngineRepository, TimerDueDateEvaluator timerDueDateEvaluator,
                               CorrelationKeyResolver correlationKeyResolver, KikwiflowConfig kikwiflowConfig,
                               CriticalEventRecorder criticalEventRecorder) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.timerDueDateEvaluator = timerDueDateEvaluator;
        this.correlationKeyResolver = correlationKeyResolver;
        this.kikwiflowConfig = kikwiflowConfig;
        this.criticalEventRecorder = criticalEventRecorder;
    }

    public ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask, ProcessDefinition processDefinition){
        return this.handleContinuation(executionResult, completedExternalTask, null, processDefinition, null);
    }

    /**
     * @param actorId quem comandou o complete (ver {@code KikwiflowEngine.completeExternalTask}), usado apenas
     *               para o evento {@code EXTERNAL_TASK_COMPLETED} — pode ser {@code null} quando não informado.
     *               Não afeta em nada a execução: o complete continua soberano independentemente de quem for
     *               o assignee da tarefa.
     */
    public ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask, ProcessDefinition processDefinition, String actorId){
        return this.handleContinuation(executionResult, completedExternalTask, null, processDefinition, actorId);
    }

    public ProcessInstance handleContinuation(ExecutionResult executionResult, ExecutableTask completedExecutableTask, ProcessDefinition processDefinition){
        return this.handleContinuation(executionResult, null, completedExecutableTask, processDefinition, null);
    }

    /**
     * @param actorId quem iniciou a instância (ver {@code KikwiflowEngine.ProcessStarter.byActor}), usado apenas
     *               para o evento {@code PROCESS_INSTANCE_STARTED} — pode ser {@code null} quando não informado.
     */
    public ProcessInstance handleContinuation(ExecutionResult executionResult, ProcessDefinition processDefinition, String actorId){
        return this.handleContinuation(executionResult, null, null, processDefinition, actorId);
    }

    private ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask,
                                               ExecutableTask completedExecutableTask, ProcessDefinition processDefinition,
                                               String actorId) {

        Continuation continuation = executionResult.continuation();
        ExecutionOutcome executionOutcome = executionResult.outcome();
        ProcessInstanceExecution processInstanceExecution = executionResult.outcome().processInstance();

        if (processInstanceExecution.getActiveNodes() == null) {
            processInstanceExecution.setActiveNodes(new java.util.HashMap<>());
        }

        List<ExecutableTask> nextExecutableTasks = new ArrayList<>();
        List<ExternalTask> nextExternalTasks = new ArrayList<>();

        String currentBranchId = null;
        String currentJoinTaskId = null;

        if (completedExternalTask != null) {
            currentBranchId = completedExternalTask.branchId();
            currentJoinTaskId = completedExternalTask.joinTaskId();

        } else if (completedExecutableTask != null) {
            if (completedExecutableTask.type() == ExecutableTaskType.NON_INTERRUPTIVE_TIMER) {
                currentBranchId = UUID.randomUUID().toString();
                currentJoinTaskId = null;
            } else {
                currentBranchId = completedExecutableTask.branchId();
                currentJoinTaskId = completedExecutableTask.joinTaskId();
            }
        }

        if (isAsyncContinuation(continuation)) {

            if (continuation.targetJoinNode() != null) {
                String newJoinTaskId = UUID.randomUUID().toString();
                List<String> branchIds = new ArrayList<>();

                for (int i = 0; i < continuation.nextNodes().size(); i++) {
                    branchIds.add(UUID.randomUUID().toString());
                }

                for (int i = 0; i < continuation.nextNodes().size(); i++) {
                    FlowNodeDefinition nextNode = continuation.nextNodes().get(i);
                    String branchId = branchIds.get(i);
                    generateNextTasksWithContext(nextNode, processInstanceExecution, branchId, newJoinTaskId, nextExecutableTasks, nextExternalTasks, processDefinition);
                }

                ExecutableTask joinTask = ExecutableTask.builder()
                        .id(newJoinTaskId)
                        .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                        .taskDefinitionId(continuation.targetJoinNode().id())
                        .processInstanceId(processInstanceExecution.getId())
                        .type(ExecutableTaskType.JOIN_GATEWAY)
                        .status(ExecutableTaskStatus.AWAITING_BRANCHES)
                        .pendingBranchIds(branchIds)
                        .branchId(currentBranchId)
                        .joinTaskId(currentJoinTaskId)
                        .build();

                nextExecutableTasks.add(joinTask);

            } else {

                String finalCurrentBranchId = currentBranchId;
                String finalCurrentJoinTaskId = currentJoinTaskId;
                continuation.nextNodes().forEach(flowNodeDefinitionSnapshot -> {
                    generateNextTasksWithContext(flowNodeDefinitionSnapshot, processInstanceExecution, finalCurrentBranchId, finalCurrentJoinTaskId, nextExecutableTasks, nextExternalTasks, processDefinition);
                });
            }

        } else if (completedExecutableTask == null || completedExecutableTask.type() != ExecutableTaskType.NON_INTERRUPTIVE_TIMER) {
            processInstanceExecution.setEndedAt(Instant.now());
            processInstanceExecution.setStatus(ProcessInstanceStatus.COMPLETED);
        }
        // Um ciclo de NON_INTERRUPTIVE_TIMER que resolve para uma continuação síncrona/nula (ex.: suas próprias
        // arestas de saída terminam num DEFAULT_END_EVENT dedicado, ou não tem saída nenhuma) está concluindo
        // apenas o seu próprio laço privado de recorrência — isso não deve nunca ditar o destino da instância
        // principal (que segue no estado em que já estava, ex.: ACTIVE aguardando o nó pai). Sem este guard, o
        // primeiro disparo de um timer não-interruptivo anexado fora de uma ramificação paralela (branchId nulo)
        // marcava a instância inteira como COMPLETED e a apagava do runtime, deixando o nó pai órfão.

        List<OutboxEventEntity> events = new ArrayList<>(executionOutcome.events());
        criticalEventRecorder.recordProcessInstanceFinished(events, processInstanceExecution, processDefinition);

        // O overload de 2 argumentos (sem tarefa concluída) só é chamado por ProcessStarter.execute() — é o
        // único ponto de entrada onde tanto completedExternalTask quanto completedExecutableTask são null,
        // o que o torna um sinal confiável de "esta é a primeira continuação da instância".
        if (completedExternalTask == null && completedExecutableTask == null) {
            criticalEventRecorder.recordProcessInstanceStarted(events, processInstanceExecution, processDefinition, actorId);
        }

        ProcessInstance processInstanceToSave = ProcessInstanceMapper.mapToRecord(processInstanceExecution);
        List<String> executableTasksToDelete = new ArrayList<>();
        List<String> externalTasksToDelete = new ArrayList<>();
        List<String> finishedNodeDefinitions = new ArrayList<>();

        // Guard de finalização (ver Javadoc de UnitOfWork.finalizingNodeId): identifica, para este commit, o
        // único nó cuja existência a transação exige — o pai sendo interrompido por um boundary event, ou o
        // próprio nó concluindo normalmente enquanto tem boundary events vigiando-o. Nunca os dois ao mesmo
        // tempo (um boundary event não tem seus próprios boundaryEventIds), então não há ambiguidade em qual
        // atribuição vale por último.
        String finalizingNodeId = null;
        AttachedTaskType finalizingNodeType = null;

        if (completedExecutableTask != null) {
            executableTasksToDelete.add(completedExecutableTask.id());
            finishedNodeDefinitions.add(completedExecutableTask.taskDefinitionId());

            if (completedExecutableTask.attachedToRefId() != null) {
                if (completedExecutableTask.type().equals(ExecutableTaskType.INTERRUPTIVE_TIMER)) {
                    finalizingNodeId = completedExecutableTask.attachedToRefId();
                    finalizingNodeType = completedExecutableTask.attachedToRefType();

                    if (completedExecutableTask.attachedToRefType().equals(AttachedTaskType.EXECUTABLE_TASK)) {
                        executableTasksToDelete.add(completedExecutableTask.attachedToRefId());
                    } else {
                        externalTasksToDelete.add(completedExecutableTask.attachedToRefId());
                    }

                    finishedNodeDefinitions.add(completedExecutableTask.attachedToRefDefinitionId());
                    criticalEventRecorder.recordInterruptedFlowNode(events, processInstanceExecution, processDefinition,
                            completedExecutableTask.attachedToRefDefinitionId(), completedExecutableTask.taskDefinitionId());
                }else if(completedExecutableTask.type().equals(ExecutableTaskType.NON_INTERRUPTIVE_TIMER)) {

                    ProcessDefinition processDef = kikwiEngineRepository.findProcessDefinitionById(processInstanceExecution.getProcessDefinitionId()).orElseThrow();
                    io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition timerDef =
                            (io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition) processDef.flowNodes().get(completedExecutableTask.taskDefinitionId());

                    // occurrence é null em ExecutableTasks anteriores a este campo (compat com dados já
                    // persistidos) — trata como se fosse o 1º ciclo, igual ao comportamento legado sem bound.
                    int firedOccurrence = completedExecutableTask.occurrence() != null ? completedExecutableTask.occurrence() : 1;
                    int nextOccurrence = firedOccurrence + 1;
                    Instant nextDueDate = timerDueDateEvaluator.calculateNextSchedule(timerDef.schedulePolicy(), nextOccurrence);

                    criticalEventRecorder.recordTimerFired(events, processInstanceExecution,
                            completedExecutableTask.taskDefinitionId(), nextDueDate);

                    if (nextDueDate != null) {

                        ExecutableTask nextTimerCycle = completedExecutableTask.toBuilder()
                                .id(UUID.randomUUID().toString())
                                .dueDate(nextDueDate)
                                .status(ExecutableTaskStatus.PENDING)
                                .acquiredAt(null)
                                .type(completedExecutableTask.type())
                                .executorId(null)
                                .error(null)
                                .executions(0L)
                                .occurrence(nextOccurrence)
                                .build();

                        nextExecutableTasks.add(nextTimerCycle);
                    }
                    // nextDueDate == null aqui (SchedulePolicy.maxOccurrences atingido, ou FIXED_DATES
                    // esgotada) encerra o laço de recorrência silenciosamente — mesmo caminho que já existia
                    // pra FIXED_DATES: PARENT_WAIT segue intocado, sem ExecutableTask órfã.

                }else {
                    throw new NotImplementedException("Comportamento não implementado para evento de borda " + completedExecutableTask.type());
                }

            }

            if (completedExecutableTask.boundaryEvents() != null && !completedExecutableTask.boundaryEvents().isEmpty()) {
                finalizingNodeId = completedExecutableTask.id();
                finalizingNodeType = AttachedTaskType.EXECUTABLE_TASK;

                completedExecutableTask.boundaryEvents().forEach(eventRef -> {
                    if (eventRef.instanceType() == AttachedTaskType.EXTERNAL_TASK) {
                        externalTasksToDelete.add(eventRef.instanceId());
                    } else {
                        executableTasksToDelete.add(eventRef.instanceId());
                    }
                });
            }
        }

        if (completedExternalTask != null) {
            externalTasksToDelete.add(completedExternalTask.id());
            finishedNodeDefinitions.add(completedExternalTask.taskDefinitionId());
            criticalEventRecorder.recordExternalTaskCompleted(events, completedExternalTask, actorId);
            if (completedExternalTask.attachedToRefId() != null) {
                finalizingNodeId = completedExternalTask.attachedToRefId();
                finalizingNodeType = completedExternalTask.attachedToRefType();

                if (completedExternalTask.attachedToRefType().equals(AttachedTaskType.EXECUTABLE_TASK)) {
                    executableTasksToDelete.add(completedExternalTask.attachedToRefId());
                } else {
                    externalTasksToDelete.add(completedExternalTask.attachedToRefId());
                }
                finishedNodeDefinitions.add(completedExternalTask.attachedToRefDefinitionId());
                criticalEventRecorder.recordInterruptedFlowNode(events, processInstanceExecution, processDefinition,
                        completedExternalTask.attachedToRefDefinitionId(), completedExternalTask.taskDefinitionId());
            }
            if (completedExternalTask.boundaryEvents() != null && !completedExternalTask.boundaryEvents().isEmpty()) {
                finalizingNodeId = completedExternalTask.id();
                finalizingNodeType = AttachedTaskType.EXTERNAL_TASK;

                completedExternalTask.boundaryEvents().forEach(eventRef -> {
                    if (eventRef.instanceType() == AttachedTaskType.EXTERNAL_TASK) {
                        externalTasksToDelete.add(eventRef.instanceId());
                    } else {
                        executableTasksToDelete.add(eventRef.instanceId());
                    }
                    finishedNodeDefinitions.add(eventRef.definitionId());
                });
            }
        }

        ProcessInstance instanceToCreate = null;
        ProcessInstance instanceToUpdate = null;
        ProcessInstance instanceToDelete = null;

        boolean isProcessCompleted = ProcessInstanceStatus.COMPLETED.equals(processInstanceToSave.status());
        boolean isPersisted = processInstanceExecution.isPersisted();

        // CALL_ACTIVITY_COORDINATOR (ver docs/engine/20-subprocessos-call-activity-especificacao.md, §4.4):
        // quando ESTA instância (o filho) conclui e carrega parentInstanceId/callerTaskId/callerBranchId
        // (setados por KikwiflowEngine.executeCallActivityStarter via ProcessStarter.asChildOf), registra a
        // mesma BranchPullIntention que PARALLEL_GATEWAY/JOIN_GATEWAY já usam para fan-in — só que atravessando
        // a fronteira de instância: o commitWork abaixo (a própria conclusão do filho) tenta liberar a branch
        // na coordenadora do pai, numa transação só. Precisa ser capturado ANTES da leitura de
        // getBranchPullIntentions() logo abaixo (a intenção só existe a partir daqui).
        if (isProcessCompleted && processInstanceExecution.getParentInstanceId() != null
                && processInstanceExecution.getCallerTaskId() != null
                && processInstanceExecution.getCallerBranchId() != null) {
            processInstanceExecution.registerBranchConclusion(
                    processInstanceExecution.getCallerTaskId(), processInstanceExecution.getCallerBranchId());
        }

        List<BranchPullIntention> intentions = processInstanceExecution.getBranchPullIntentions();

        if (isProcessCompleted) {
            if (isPersisted) instanceToDelete = processInstanceToSave;
        } else {
            if (isPersisted) {
                instanceToUpdate = processInstanceToSave;
            } else {
                instanceToCreate = processInstanceToSave;
                processInstanceExecution.setPersisted(true);
            }
        }

        UnitOfWork updatedUnitOfWork = new UnitOfWork(
                instanceToCreate, instanceToUpdate, instanceToDelete,
                nextExecutableTasks, nextExternalTasks, executableTasksToDelete,
                null, externalTasksToDelete, events, null, null, null,
                finishedNodeDefinitions,
                intentions,
                processInstanceExecution.getVariableOperations(),
                finalizingNodeId,
                finalizingNodeType
        );

        kikwiEngineRepository.commitWork(updatedUnitOfWork);

        processInstanceExecution.clearBranchPullIntentions();
        processInstanceExecution.clearVariableOperations();

        return processInstanceToSave;
    }

    private void generateNextTasksWithContext(FlowNodeDefinition flowNodeDefinition,
                                              ProcessInstanceExecution processInstanceExecution,
                                              String branchId,
                                              String joinTaskId,
                                              List<ExecutableTask> nextExecutableTasks,
                                              List<ExternalTask> nextExternalTasks,
                                              ProcessDefinition processDefinition) {

        String flowNodeDefinitionId = flowNodeDefinition.id();
        String processInstanceId = processInstanceExecution.getId();
        String processDefinitionId = processInstanceExecution.getProcessDefinitionId();

        if (flowNodeDefinition instanceof ExternalTaskDefinition mt) {
            String externalTaskNodeId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = generateBoundaryEvents(
                    mt.boundaryEventIds(), externalTaskNodeId, AttachedTaskType.EXTERNAL_TASK, flowNodeDefinitionId,
                    branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

            ExternalTask externalTask = ExternalTask.builder()
                    .id(externalTaskNodeId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .name(flowNodeDefinition.name())
                    .description(flowNodeDefinition.description())
                    .boundaryEvents(boundaryEvents)
                    .tenantId(processInstanceExecution.getTenantId())
                    .branchId(branchId)
                    .joinTaskId(joinTaskId)
                    .build();

            nextExternalTasks.add(externalTask);

        } else if (flowNodeDefinition instanceof ExecutableTaskDefinition st) {
            String executableTaskNodeId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = generateBoundaryEvents(
                    st.boundaryEventIds(), executableTaskNodeId, AttachedTaskType.EXECUTABLE_TASK, flowNodeDefinitionId,
                    branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

            long initialRetries = kikwiflowConfig.getDefaultMaxRetries();
            if (st.retryPolicy() != null) {
                initialRetries = st.retryPolicy().maxRetries();
            }

            ExecutableTask executableTask = ExecutableTask.builder()
                    .id(executableTaskNodeId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .type(ExecutableTaskType.STANDARD)
                    .boundaryEvents(boundaryEvents)
                    .retryPolicy(st.retryPolicy())
                    .branchId(branchId)
                    .joinTaskId(joinTaskId)
                    .retries(initialRetries)
                    .build();

            nextExecutableTasks.add(executableTask);
        } else if (flowNodeDefinition instanceof EventCatcherDefinition ec) {

            List<CorrelationItem> items = correlationKeyResolver.resolve(ec, processInstanceExecution);

            if (ec.catchType() == CatchType.STANDALONE) {
                String taskId = UUID.randomUUID().toString();
                CorrelationItem item = items.get(0);
                List<AttachedEventReference> boundaryEvents = generateBoundaryEvents(
                        ec.boundaryEventIds(), taskId, AttachedTaskType.EXTERNAL_TASK, flowNodeDefinitionId,
                        branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

                nextExternalTasks.add(ExternalTask.builder()
                        .id(taskId)
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionId(flowNodeDefinitionId)
                        .processInstanceId(processInstanceId)
                        .name(flowNodeDefinition.name())
                        .description(flowNodeDefinition.description())
                        .type(ExternalTaskType.EVENT_CATCHER_STANDALONE)
                        .correlationKey(item.key())
                        .displayName(item.displayName())
                        .boundaryEvents(boundaryEvents)
                        .tenantId(processInstanceExecution.getTenantId())
                        .branchId(branchId)
                        .joinTaskId(joinTaskId)
                        .build());

            } else { // GROUP
                String parentId = UUID.randomUUID().toString();
                List<String> pendingKeys = items.stream().map(CorrelationItem::key).toList();

                items.forEach(item -> nextExternalTasks.add(ExternalTask.builder()
                        .id(UUID.randomUUID().toString())
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionId(flowNodeDefinitionId)
                        .processInstanceId(processInstanceId)
                        .name(flowNodeDefinition.name())
                        .type(ExternalTaskType.EVENT_CATCHER_CHILD)
                        .correlationKey(item.key())
                        .displayName(item.displayName())
                        .matchPolicy(ec.matchPolicy())
                        .coordinatorTaskId(parentId)
                        .tenantId(processInstanceExecution.getTenantId())
                        .build()));

                List<AttachedEventReference> boundaryEvents = generateBoundaryEvents(
                        ec.boundaryEventIds(), parentId, AttachedTaskType.EXTERNAL_TASK, flowNodeDefinitionId,
                        branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

                nextExternalTasks.add(ExternalTask.builder()
                        .id(parentId)
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionId(flowNodeDefinitionId)
                        .processInstanceId(processInstanceId)
                        .name(flowNodeDefinition.name())
                        .description(flowNodeDefinition.description())
                        .type(ExternalTaskType.EVENT_CATCHER_PARENT)
                        .pendingCorrelationKeys(pendingKeys)
                        .totalCorrelationKeys(pendingKeys.size())
                        .matchPolicy(ec.matchPolicy())
                        .boundaryEvents(boundaryEvents)
                        .tenantId(processInstanceExecution.getTenantId())
                        .branchId(branchId)
                        .joinTaskId(joinTaskId)
                        .build());
            }
        } else if (flowNodeDefinition instanceof EventThrowerDefinition et) {

            long initialRetries = kikwiflowConfig.getDefaultMaxRetries();

            nextExecutableTasks.add(ExecutableTask.builder()
                    .id(UUID.randomUUID().toString())
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .type(ExecutableTaskType.EVENT_THROW)
                    .dueDate(Instant.now())
                    .retries(initialRetries)
                    .branchId(branchId)
                    .joinTaskId(joinTaskId)
                    .build());

        } else if (flowNodeDefinition instanceof TimerTaskDefinition tt) {

            Instant resolvedDueDate = timerDueDateEvaluator.resolveDueDate(tt, processInstanceExecution);
            String timerTaskId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = generateBoundaryEvents(
                    tt.boundaryEventIds(), timerTaskId, AttachedTaskType.EXECUTABLE_TASK, flowNodeDefinitionId,
                    branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

            nextExecutableTasks.add(ExecutableTask.builder()
                    .id(timerTaskId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .type(ExecutableTaskType.TIMER_TASK)
                    .dueDate(resolvedDueDate)
                    .boundaryEvents(boundaryEvents)
                    .branchId(branchId)
                    .joinTaskId(joinTaskId)
                    .build());
        } else if (flowNodeDefinition instanceof CallActivityDefinition ca) {
            generateCallActivityFanOut(ca, processInstanceExecution, branchId, joinTaskId, nextExecutableTasks, nextExternalTasks, processDefinition);
        } else {
            if (joinTaskId != null && branchId != null) {
                processInstanceExecution.registerBranchConclusion(joinTaskId, branchId);
            }
        }
    }

    /**
     * Mecanismo único de materialização de boundary events — chamado por todo nó que suporta
     * {@code boundaryEventIds} ({@code ExternalTaskDefinition}, {@code ExecutableTaskDefinition},
     * {@code EventCatcherDefinition} nos dois modos, {@code CallActivityDefinition}, {@code TimerTaskDefinition}).
     * Antes desta extração, cada um desses 6 pontos de chamada tinha sua própria cópia quase idêntica deste
     * loop — cada boundary event definition já carrega tudo que {@link #getInterruptiveExecutableTask}/
     * {@link #getNonInterruptiveTimerTask}/{@link #getBoundaryCatchEventTask} precisam via parâmetro, nenhum
     * deles conhece ou depende do tipo do nó pai, então um dispatch só cobre qualquer combinação.
     *
     * <p><b>"Quais tipos de boundary event valem em qual tipo de nó pai" não é decidido aqui</b> — é política,
     * resolvida em deploy-time por {@code DeployValidator.validateBoundaryEvents} (uma allowlist por tipo de
     * host). O {@code throw} no fim deste método é só defesa contra um tipo de nó realmente desconhecido, não
     * o mecanismo de rejeição — ao contrário de como cada cópia funcionava antes desta unificação.
     *
     * @param mainTaskId id, já gerado antes do nó pai existir de fato, que os boundary events vão referenciar
     *                   via {@code attachedToRefId} (timers) ou {@code attachedToRefId} (catch event) — é por
     *                   isso que todo chamador gera o id da task principal antes de construí-la
     * @param mainTaskType em qual coleção {@code mainTaskId} vai viver — {@code EXECUTABLE_TASK} ou
     *                     {@code EXTERNAL_TASK} — necessário pro guard de finalização genérico saber de qual
     *                     coleção apagar quando o boundary event disparar
     * @return as referências a anexar em {@code boundaryEvents()} da task principal; um
     *         {@code NonInterruptiveTimerEventDefinition} cujo {@code schedulePolicy} já resolve vazio não gera
     *         task nenhuma e não entra no retorno
     */
    private List<AttachedEventReference> generateBoundaryEvents(
            List<String> boundaryEventIds,
            String mainTaskId,
            AttachedTaskType mainTaskType,
            String flowNodeDefinitionId,
            String branchId,
            String joinTaskId,
            ProcessInstanceExecution processInstanceExecution,
            ProcessDefinition processDefinition,
            List<ExecutableTask> nextExecutableTasks,
            List<ExternalTask> nextExternalTasks) {

        List<AttachedEventReference> boundaryEvents = new ArrayList<>();

        if (boundaryEventIds == null || boundaryEventIds.isEmpty()) {
            return boundaryEvents;
        }

        String processInstanceId = processInstanceExecution.getId();
        String processDefinitionId = processInstanceExecution.getProcessDefinitionId();

        boundaryEventIds.forEach(boundaryEventDefinitionId -> {
            FlowNodeDefinition boundaryEventDefinition = processDefinition.flowNodes().get(boundaryEventDefinitionId);

            if (boundaryEventDefinition instanceof InterruptiveTimerEventDefinition it) {
                ExecutableTask boundaryEvent = getInterruptiveExecutableTask(
                        mainTaskId, processInstanceId, it, processDefinitionId, mainTaskType,
                        flowNodeDefinitionId, branchId, joinTaskId, processInstanceExecution);

                boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id(), AttachedTaskType.EXECUTABLE_TASK));
                nextExecutableTasks.add(boundaryEvent);

            } else if (boundaryEventDefinition instanceof NonInterruptiveTimerEventDefinition nit) {
                ExecutableTask boundaryEvent = getNonInterruptiveTimerTask(
                        mainTaskId, processInstanceId, nit, processDefinitionId, mainTaskType,
                        flowNodeDefinitionId, branchId, joinTaskId);

                if (boundaryEvent != null) {
                    boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id(), AttachedTaskType.EXECUTABLE_TASK));
                    nextExecutableTasks.add(boundaryEvent);
                }

            } else if (boundaryEventDefinition instanceof InterruptiveCatchEventDefinition ice) {
                ExternalTask boundaryEvent = getBoundaryCatchEventTask(
                        mainTaskId, processInstanceId, ice, processDefinitionId, mainTaskType,
                        flowNodeDefinitionId, branchId, joinTaskId, processInstanceExecution);

                boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id(), AttachedTaskType.EXTERNAL_TASK));
                nextExternalTasks.add(boundaryEvent);

            } else if (boundaryEventDefinition instanceof ErrorHandlerDefinition) {
                // BOUNDARY_ERROR_HANDLER não materializa task nenhuma — try/catch síncrono resolvido inline em
                // ProcessExecutionManager.executeFlow/Navigator.findMatchingErrorHandler. Só listado aqui pra
                // não cair no throw abaixo; DeployValidator já garante que só chega aqui quando o nó pai
                // realmente suporta esse tipo (hoje, só ExecutableTaskDefinition).

            } else {
                throw new NotImplementedException("Processamento de tarefa de borda não implementado para o tipo "
                        + (boundaryEventDefinition != null ? boundaryEventDefinition.type() : boundaryEventDefinitionId));
            }
        });

        return boundaryEvents;
    }

    /**
     * Alcançar um {@code CALL_ACTIVITY_COORDINATOR} gera, nesta única chamada (mesma transação do nó anterior
     * — ver {@code ProcessExecutionManager.isCommitBefore}), 1 {@code ExecutableTask} coordenadora
     * ({@code AWAITING_BRANCHES}, ou {@code PENDING} direto se a coleção resolver vazia — nada para esperar)
     * + N {@code ExecutableTask} iniciadoras ({@code CALL_ACTIVITY_STARTER}, {@code PENDING}), mesmo padrão
     * "um nó → N tarefas-filha compartilhando um id coordenador" do modo GROUP de {@code EventCatcherDefinition}
     * acima. Nenhuma delas dispara efeito colateral real — só metadado; quem de fato chama
     * {@code KikwiflowEngine.startProcess()} é a retomada individual de cada iniciadora (ver
     * {@code KikwiflowEngine.executeFromTask}), em transação própria.
     */
    private void generateCallActivityFanOut(CallActivityDefinition ca,
                                            ProcessInstanceExecution processInstanceExecution,
                                            String branchId,
                                            String joinTaskId,
                                            List<ExecutableTask> nextExecutableTasks,
                                            List<ExternalTask> nextExternalTasks,
                                            ProcessDefinition processDefinition) {

        String flowNodeDefinitionId = ca.id();
        String processInstanceId = processInstanceExecution.getId();
        String processDefinitionId = processInstanceExecution.getProcessDefinitionId();

        List<Object> elements;
        if (ca.collectionVariable() == null) {
            elements = new ArrayList<>();
            elements.add(null);
        } else {
            Map<String, ProcessVariable> variables = processInstanceExecution.getVariables();
            ProcessVariable collectionVar = variables != null ? variables.get(ca.collectionVariable()) : null;
            Object rawValue = collectionVar != null ? collectionVar.value() : null;

            if (!(rawValue instanceof List<?> rawList)) {
                throw new IllegalStateException("CALL_ACTIVITY_COORDINATOR '" + flowNodeDefinitionId
                        + "': a variável de coleção '" + ca.collectionVariable()
                        + "' precisa resolver para uma lista em tempo de execução, mas era "
                        + (rawValue == null ? "nula" : rawValue.getClass().getName()) + ".");
            }
            elements = new ArrayList<>(rawList);
        }

        String coordinatorTaskId = UUID.randomUUID().toString();
        List<String> branchIds = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            branchIds.add(coordinatorTaskId + ":" + i);
        }

        List<ProcessVariable> loopElementVars = new ArrayList<>();
        for (Object element : elements) {
            loopElementVars.add(ca.elementVariable() != null ? new ProcessVariable(ca.elementVariable(), element) : null);
        }

        long initialRetries = kikwiflowConfig.getDefaultMaxRetries();

        // Modo SEQUENTIAL: só a iniciadora do índice 0 é criada agora nesta transação — as demais são criadas
        // uma a uma, sob demanda, conforme cada filho anterior completa (ver
        // KikwiflowEngine.advanceSequentialCallActivity). Isso mantém pendingBranchIds da coordenadora sempre
        // com no máximo 1 item em modo sequencial, reaproveitando o $pull genérico de BranchPullIntention sem
        // nenhuma mudança nos repositórios (InMemoryKikwiEngineRepository/MongoKikwiEngineRepository) — ver
        // docs/engine/20-subprocessos-call-activity-especificacao.md.
        boolean sequential = ca.iterationMode() == CallActivityIterationMode.SEQUENTIAL;
        int startersToCreateNow = sequential ? Math.min(1, elements.size()) : elements.size();

        for (int i = 0; i < startersToCreateNow; i++) {
            nextExecutableTasks.add(ExecutableTask.builder()
                    .id(UUID.randomUUID().toString())
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .type(ExecutableTaskType.CALL_ACTIVITY_STARTER)
                    .status(ExecutableTaskStatus.PENDING)
                    .joinTaskId(coordinatorTaskId)
                    .branchId(branchIds.get(i))
                    .loopIndex(i)
                    .loopElement(loopElementVars.get(i))
                    .retries(initialRetries)
                    .build());
        }

        List<AttachedEventReference> coordinatorBoundaryEvents = generateBoundaryEvents(
                ca.boundaryEventIds(), coordinatorTaskId, AttachedTaskType.EXECUTABLE_TASK, flowNodeDefinitionId,
                branchId, joinTaskId, processInstanceExecution, processDefinition, nextExecutableTasks, nextExternalTasks);

        // Em modo SEQUENTIAL, pendingBranchIds da coordenadora carrega só o branch em voo (não todos os N) —
        // igual a um "join de tamanho 1"; pendingLoopElements guarda a cauda ainda não iniciada da lista
        // resolvida (null quando não há mais nada a iterar depois do branch atual, ou em modo PARALLEL).
        List<String> coordinatorPendingBranchIds = sequential
                ? (branchIds.isEmpty() ? List.of() : List.of(branchIds.get(0)))
                : branchIds;
        List<ProcessVariable> coordinatorPendingLoopElements = sequential && loopElementVars.size() > 1
                ? new ArrayList<>(loopElementVars.subList(1, loopElementVars.size()))
                : null;

        nextExecutableTasks.add(ExecutableTask.builder()
                .id(coordinatorTaskId)
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(flowNodeDefinitionId)
                .processInstanceId(processInstanceId)
                .type(ExecutableTaskType.CALL_ACTIVITY_COORDINATOR)
                .status(coordinatorPendingBranchIds.isEmpty() ? ExecutableTaskStatus.PENDING : ExecutableTaskStatus.AWAITING_BRANCHES)
                .pendingBranchIds(coordinatorPendingBranchIds)
                .pendingLoopElements(coordinatorPendingLoopElements)
                .loopIndex(sequential && !branchIds.isEmpty() ? 0 : null)
                .boundaryEvents(coordinatorBoundaryEvents)
                .branchId(branchId)
                .joinTaskId(joinTaskId)
                .build());
    }

    private ExecutableTask getNonInterruptiveTimerTask(
            String mainTaskId,
            String processInstanceId,
            io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition timerDef,
            String processDefinitionId,
            AttachedTaskType mainTaskType,
            String flowNodeDefinitionId,
            String branchId,
            String joinTaskId) {

        // maxOccurrences = 0 (ou negativo) já esgota aqui, no 1º ciclo — o boundary event nem chega a existir
        // em runtime, mesmo comportamento de um schedulePolicy nulo (ver Javadoc de calculateNextSchedule).
        Instant firstDueDate = timerDueDateEvaluator.calculateNextSchedule(timerDef.schedulePolicy(), 1);

        if (firstDueDate == null) {
            return null;
        }

        return ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
                .type(ExecutableTaskType.NON_INTERRUPTIVE_TIMER)
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(timerDef.id())
                .processInstanceId(processInstanceId)
                .dueDate(firstDueDate)
                .occurrence(1)
                .attachedToRefId(mainTaskId)
                .attachedToRefType(mainTaskType)
                .attachedToRefDefinitionId(flowNodeDefinitionId)
                .branchId(branchId)
                .joinTaskId(joinTaskId)
                .build();
    }


    private ExecutableTask getInterruptiveExecutableTask(
            String mainTaskId,
            String processInstanceId,
            InterruptiveTimerEventDefinition timerDef,
            String processDefinitionId,
            AttachedTaskType mainTaskType,
            String flowNodeDefinitionId,
            String branchId,
            String joinTaskId,
            ProcessInstanceExecution executionContext) {

        Instant resolvedDueDate = timerDueDateEvaluator.resolveDueDate(timerDef, executionContext);

        return ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
                .type(ExecutableTaskType.INTERRUPTIVE_TIMER)
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(timerDef.id())
                .processInstanceId(processInstanceId)
                .dueDate(resolvedDueDate)
                .attachedToRefId(mainTaskId)
                .attachedToRefType(mainTaskType)
                .attachedToRefDefinitionId(flowNodeDefinitionId)
                .branchId(branchId)
                .joinTaskId(joinTaskId)
                .name(timerDef.name())
                .description(timerDef.description())
                .build();
    }

    /**
     * Constrói a espera de correlação de um {@link InterruptiveCatchEventDefinition} anexado como boundary
     * event — ao contrário de um timer (ExecutableTask), essa espera é uma ExternalTask (mesma natureza de um
     * EVENT_CATCHER STANDALONE), pois depende de um gatilho externo via {@code correlateMessage}, não de um
     * prazo. {@code attachedToRefId}/{@code attachedToRefType} apontam para o nó pai exatamente como em
     * {@link #getInterruptiveExecutableTask} — é isso que faz a correlação cancelar o pai através do mesmo
     * caminho genérico de {@code handleContinuation} usado por timers interruptivos.
     */
    private ExternalTask getBoundaryCatchEventTask(
            String mainTaskId,
            String processInstanceId,
            InterruptiveCatchEventDefinition catchEventDef,
            String processDefinitionId,
            AttachedTaskType mainTaskType,
            String flowNodeDefinitionId,
            String branchId,
            String joinTaskId,
            ProcessInstanceExecution executionContext) {

        CorrelationItem item = correlationKeyResolver.resolve(catchEventDef, executionContext).get(0);

        return ExternalTask.builder()
                .id(UUID.randomUUID().toString())
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(catchEventDef.id())
                .processInstanceId(processInstanceId)
                .name(catchEventDef.name())
                .description(catchEventDef.description())
                .type(ExternalTaskType.BOUNDARY_INTERRUPTIVE_CATCH_EVENT)
                .correlationKey(item.key())
                .displayName(item.displayName())
                .attachedToRefId(mainTaskId)
                .attachedToRefType(mainTaskType)
                .attachedToRefDefinitionId(flowNodeDefinitionId)
                .tenantId(executionContext.getTenantId())
                .branchId(branchId)
                .joinTaskId(joinTaskId)
                .build();
    }

    private Instant parseDuration(String duration){
        return Instant.now().plus(Duration.parse(duration));
    }

    private boolean isAsyncContinuation(Continuation continuation){
        return continuation != null && continuation.isAsynchronous();
    }
}
