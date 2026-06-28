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
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
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
import java.util.Objects;
import java.util.UUID;

public class ContinuationService {

    private final KikwiEngineRepository kikwiEngineRepository;
    private final KikwiflowConfig kikwiflowConfig;

    public ContinuationService(KikwiEngineRepository kikwiEngineRepository, KikwiflowConfig kikwiflowConfig) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    public ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask){
        return this.handleContinuation(executionResult, completedExternalTask, null);
    }

    public ProcessInstance handleContinuation(ExecutionResult executionResult, ExecutableTask completedExecutableTask){
        return this.handleContinuation(executionResult, null, completedExecutableTask);
    }

    public ProcessInstance handleContinuation(ExecutionResult executionResult){
        return this.handleContinuation(executionResult, null, null);
    }

    private ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask,
                                               ExecutableTask completedExecutableTask) {

        Continuation continuation = executionResult.continuation();
        ExecutionOutcome executionOutcome = executionResult.outcome();
        ProcessInstanceExecution processInstanceExecution = executionResult.outcome().processInstance();
        List<BranchPullIntention> intentions = processInstanceExecution.getBranchPullIntentions();

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
            currentBranchId = completedExecutableTask.branchId();
            currentJoinTaskId = completedExecutableTask.joinTaskId();
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
                    generateNextTasksWithContext(nextNode, processInstanceExecution, branchId, newJoinTaskId, nextExecutableTasks, nextExternalTasks);
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
                    generateNextTasksWithContext(flowNodeDefinitionSnapshot, processInstanceExecution, finalCurrentBranchId, finalCurrentJoinTaskId, nextExecutableTasks, nextExternalTasks);
                });
            }

        } else {
            processInstanceExecution.setEndedAt(Instant.now());
            processInstanceExecution.setStatus(ProcessInstanceStatus.COMPLETED);
        }

        List<OutboxEventEntity> events = new ArrayList<>(executionOutcome.events());
        if(kikwiflowConfig.isOutboxEventsEnabled() && ProcessInstanceStatus.COMPLETED.equals(processInstanceExecution.getStatus())){
            ProcessInstanceFinished processInstanceFinished = ProcessInstanceFinished.builder()
                    .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                    .businessKey(processInstanceExecution.getBusinessKey())
                    .id(processInstanceExecution.getId())
                    .status(processInstanceExecution.getStatus())
                    .variables(processInstanceExecution.getVariables())
                    .startedAt(processInstanceExecution.getStartedAt())
                    .endedAt(processInstanceExecution.getEndedAt())
                    .build();
            events.add(new OutboxEventEntity("PROCESS_INSTANCE_FINISHED", processInstanceFinished));
        }

        ProcessInstance processInstanceToSave = ProcessInstanceMapper.mapToRecord(processInstanceExecution);
        List<String> executableTasksToDelete = new ArrayList<>();
        List<String> externalTasksToDelete = new ArrayList<>();
        List<String> finishedNodeDefinitions = new ArrayList<>();

        if (completedExecutableTask != null) {
            executableTasksToDelete.add(completedExecutableTask.id());
            finishedNodeDefinitions.add(completedExecutableTask.taskDefinitionId());
            if (completedExecutableTask.attachedToRefId() != null) {
                if (completedExecutableTask.attachedToRefType().equals(AttachedTaskType.EXECUTABLE_TASK)) {
                    executableTasksToDelete.add(completedExecutableTask.attachedToRefId());
                } else {
                    externalTasksToDelete.add(completedExecutableTask.attachedToRefId());
                }
                finishedNodeDefinitions.add(completedExecutableTask.attachedToRefDefinitionId());
                FlowNodeFinished interruptedEvent = FlowNodeFinished.builder()
                        .flowNodeDefinitionId(completedExecutableTask.attachedToRefDefinitionId())
                        .processInstanceId(processInstanceExecution.getId())
                        .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                        .interruptedByNodeDefinitionId(completedExecutableTask.taskDefinitionId())
                        .finishedAt(Instant.now())
                        .nodeExecutionStatus(NodeExecutionStatus.INTERRUPTED)
                        .build();
                events.add(new OutboxEventEntity("FLOW_NODE_FINISHED", interruptedEvent));
            }
            if (completedExecutableTask.boundaryEvents() != null) {
                completedExecutableTask.boundaryEvents().forEach(eventRef -> executableTasksToDelete.add(eventRef.instanceId()));
            }
        }

        if (completedExternalTask != null) {
            externalTasksToDelete.add(completedExternalTask.id());
            finishedNodeDefinitions.add(completedExternalTask.taskDefinitionId());
            if (completedExternalTask.attachedToRefId() != null) {
                if (completedExternalTask.attachedToRefType().equals(AttachedTaskType.EXECUTABLE_TASK)) {
                    executableTasksToDelete.add(completedExternalTask.attachedToRefId());
                } else {
                    externalTasksToDelete.add(completedExternalTask.attachedToRefId());
                }
                finishedNodeDefinitions.add(completedExternalTask.attachedToRefDefinitionId());
                if(kikwiflowConfig.isOutboxEventsEnabled()){
                    FlowNodeFinished interruptedEvent = FlowNodeFinished.builder()
                            .flowNodeDefinitionId(completedExternalTask.attachedToRefDefinitionId())
                            .processInstanceId(processInstanceExecution.getId())
                            .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                            .interruptedByNodeDefinitionId(completedExternalTask.taskDefinitionId())
                            .finishedAt(Instant.now())
                            .nodeExecutionStatus(NodeExecutionStatus.INTERRUPTED)
                            .build();
                    events.add(new OutboxEventEntity("FLOW_NODE_FINISHED", interruptedEvent));
                }
            }
            if (completedExternalTask.boundaryEvents() != null) {
                completedExternalTask.boundaryEvents().forEach(eventRef -> {
                    executableTasksToDelete.add(eventRef.instanceId());
                    finishedNodeDefinitions.add(eventRef.definitionId());
                });
            }
        }

        ProcessInstance instanceToCreate = null;
        ProcessInstance instanceToUpdate = null;
        ProcessInstance instanceToDelete = null;

        boolean isProcessCompleted = ProcessInstanceStatus.COMPLETED.equals(processInstanceToSave.status());
        boolean isPersisted = processInstanceExecution.isPersisted();


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
                processInstanceExecution.getVariableOperations()
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
                                              List<ExternalTask> nextExternalTasks) {

        String flowNodeDefinitionId = flowNodeDefinition.id();
        String processInstanceId = processInstanceExecution.getId();
        String processDefinitionId = processInstanceExecution.getProcessDefinitionId();

        if (flowNodeDefinition instanceof ExternalTaskDefinition mt) {
            String externalTaskNodeId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = new ArrayList<>();

            if (Objects.nonNull(mt.boundaryEvents()) && !mt.boundaryEvents().isEmpty()) {
                mt.boundaryEvents().forEach(boundaryEventDefinition -> {

                    if(boundaryEventDefinition instanceof InterruptiveTimerEventDefinition it){
                        ExecutableTask boundaryEvent = getExecutableTaskFrom(externalTaskNodeId,
                                processInstanceId,
                                boundaryEventDefinition.id(),
                                processDefinitionId,
                                AttachedTaskType.EXTERNAL_TASK,
                                it.duration(),
                                flowNodeDefinitionId,
                                ExecutableTaskType.INTERRUPTIVE_TIMER, branchId, joinTaskId);

                        boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id()));
                        nextExecutableTasks.add(boundaryEvent);
                    }

                    //TODO mapear outros tipos

                });
            }

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
            List<AttachedEventReference> boundaryEvents = new ArrayList<>();
            if (Objects.nonNull(st.boundaryEvents()) && !st.boundaryEvents().isEmpty()) {
                st.boundaryEvents().forEach(boundaryEventDefinition -> {
                    if(boundaryEventDefinition instanceof InterruptiveTimerEventDefinition it){

                        ExecutableTask boundaryEvent = getExecutableTaskFrom(executableTaskNodeId,
                            processInstanceId,
                            it.id(),
                            processDefinitionId,
                            AttachedTaskType.EXECUTABLE_TASK,
                            it.duration(),
                            flowNodeDefinitionId,
                            ExecutableTaskType.INTERRUPTIVE_TIMER,
                            branchId,
                            joinTaskId);



                        nextExecutableTasks.add(boundaryEvent);
                        boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id()));
                    }

                    //TODO mapear outros tipos
                });
            }

            long initialRetries = kikwiflowConfig.getDefaultMaxRetries(); // Requer o getter que criamos na config
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
        } else {
            if (joinTaskId != null && branchId != null) {
                processInstanceExecution.registerBranchConclusion(joinTaskId, branchId);
            }
        }
    }

    private ExecutableTask getExecutableTaskFrom(String mainTaskId, String processInstanceId, String taskDefinitionId, String processDefinitionId, AttachedTaskType mainTaskType, String duration, String flowNodeDefinitionId, ExecutableTaskType taskType, String branchId, String joinTaskId){
        return ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
                .type(taskType)
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(taskDefinitionId)
                .processInstanceId(processInstanceId)
                .dueDate(parseDuration(duration))
                .attachedToRefId(mainTaskId)
                .attachedToRefType(mainTaskType)
                .attachedToRefDefinitionId(flowNodeDefinitionId)
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
