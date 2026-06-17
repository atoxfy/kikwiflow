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
import io.kikwiflow.model.execution.ProcessInstance;
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


    /**
     * Orquestra a persistência do estado final de uma execução síncrona.
     * Este método constrói e comita a {@link UnitOfWork}.
     */
    private ProcessInstance handleContinuation(ExecutionResult executionResult, ExternalTask completedExternalTask,
                                               ExecutableTask completedExecutableTask) {

        Continuation continuation = executionResult.continuation();
        ExecutionOutcome executionOutcome = executionResult.outcome();
        ProcessInstanceExecution processInstance = executionOutcome.processInstance();

        if (processInstance.getActiveNodes() == null) {
            processInstance.setActiveNodes(new java.util.HashMap<>());
        }

        List<ExecutableTask> nextExecutableTasks = new ArrayList<>();
        List<ExternalTask>  nextExternalTasks = new ArrayList<>();

        if (isAsyncContinuation(continuation)) {
            continuation.nextNodes().forEach(flowNodeDefinitionSnapshot -> {
                generateNextTasks(flowNodeDefinitionSnapshot, processInstance,  nextExecutableTasks, nextExternalTasks);
            });

        }else{
            processInstance.setEndedAt(Instant.now());
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
        }

        List<OutboxEventEntity> events = new ArrayList<>(executionOutcome.events());
        if(kikwiflowConfig.isOutboxEventsEnabled() && ProcessInstanceStatus.COMPLETED.equals(processInstance.getStatus())){

            ProcessInstanceFinished processInstanceFinished = ProcessInstanceFinished.builder()
                    .processDefinitionId(processInstance.getProcessDefinitionId())
                    .businessKey(processInstance.getBusinessKey())
                    .id(processInstance.getId())
                    .status(processInstance.getStatus())
                    .variables(processInstance.getVariables())
                    .startedAt(processInstance.getStartedAt())
                    .endedAt(processInstance.getEndedAt())
                    .build();

            events.add(new OutboxEventEntity("PROCESS_INSTANCE_FINISHED", processInstanceFinished));
        }

        ProcessInstance processInstanceToSave = ProcessInstanceMapper.mapToRecord(processInstance);
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
                        .processInstanceId(processInstance.getId())
                        .processDefinitionId(processInstance.getProcessDefinitionId())
                        .interruptedByNodeDefinitionId(completedExecutableTask.taskDefinitionId())
                        .finishedAt(Instant.now())
                        .nodeExecutionStatus(NodeExecutionStatus.INTERRUPTED)
                        .build();

                events.add(new OutboxEventEntity("FLOW_NODE_FINISHED", interruptedEvent));
            }

            if (completedExecutableTask.boundaryEvents() != null) {
                completedExecutableTask.boundaryEvents().forEach(eventRef -> {
                    executableTasksToDelete.add(eventRef.instanceId());
                });
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
                            .processInstanceId(processInstance.getId())
                            .processDefinitionId(processInstance.getProcessDefinitionId())
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

        boolean isProcessCompleted = ProcessInstanceStatus.COMPLETED.equals(processInstanceToSave.status());
        boolean isPersisted = processInstance.isPersisted();

        ProcessInstance instanceToCreate = null;
        ProcessInstance instanceToUpdate = null;
        ProcessInstance instanceToDelete = null;

        if (isProcessCompleted) {
            if (isPersisted) {
                instanceToDelete = processInstanceToSave;
            }
        } else {
            if (isPersisted) {
                instanceToUpdate = processInstanceToSave;
            } else {
                instanceToCreate = processInstanceToSave;
                processInstance.setPersisted(true);
            }
        }

        UnitOfWork updatedUnitOfWork = new UnitOfWork(
                instanceToCreate,
                instanceToUpdate,
                instanceToDelete,
                nextExecutableTasks,
                nextExternalTasks,
                executableTasksToDelete,
                null,
                externalTasksToDelete,
                events,
                null,
                null,
                finishedNodeDefinitions
        );

        kikwiEngineRepository.commitWork(updatedUnitOfWork);
        return processInstanceToSave;
    }

    private boolean isAsyncContinuation(Continuation continuation){
        return continuation != null && continuation.isAsynchronous();
    }

    private ExecutableTask getExecutableTaskFrom(String mainTaskId, String processInstanceId, String taskDefinitionId, String processDefinitionId, AttachedTaskType mainTaskType, String duration, String flowNodeDefinitionId){
        return  ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
                .processDefinitionId(processDefinitionId)
                .taskDefinitionId(taskDefinitionId)
                .processInstanceId(processInstanceId)
                .dueDate(parseDuration(duration))
                .attachedToRefId(mainTaskId)
                .attachedToRefType(mainTaskType)
                .attachedToRefDefinitionId(flowNodeDefinitionId)
                .build();
    }

    private Instant parseDuration(String duration){
        return Instant.now().plus(Duration.parse(duration));
    }

    private void generateNextTasks(FlowNodeDefinition flowNodeDefinition, ProcessInstanceExecution processInstanceExecution, List<ExecutableTask> nextExecutableTasks, List<ExternalTask> nextExternalTasks){
        String flowNodeDefinitionId = flowNodeDefinition.id();
        String processInstanceId = processInstanceExecution.getId();
        String processDefinitionId = processInstanceExecution.getProcessDefinitionId();

        if(flowNodeDefinition instanceof ExternalTaskDefinition mt){

            String externalTaskNodeId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = new ArrayList<>();
            if(Objects.nonNull(mt.boundaryEvents())
                    && !mt.boundaryEvents().isEmpty()){
                mt.boundaryEvents()
                        .forEach(boundaryEventDefinition -> {
                            ExecutableTask boundaryEvent = getExecutableTaskFrom(externalTaskNodeId,
                                    processInstanceId,
                                    boundaryEventDefinition.id(),
                                    processDefinitionId,
                                    AttachedTaskType.EXTERNAL_TASK,
                                    ((InterruptiveTimerEventDefinition)boundaryEventDefinition).duration(),
                                    flowNodeDefinitionId);

                            boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id()));
                            nextExecutableTasks.add(boundaryEvent);
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
                    .build();

            nextExternalTasks.add(externalTask);

        }else if (flowNodeDefinition instanceof ExecutableTaskDefinition st){
            String executableTaskNodeId = UUID.randomUUID().toString();
            List<AttachedEventReference> boundaryEvents = new ArrayList<>();

            if( Objects.nonNull(st.boundaryEvents())
                    && !st.boundaryEvents().isEmpty()){
                st.boundaryEvents()
                        .forEach(boundaryEventDefinition ->  {

                            ExecutableTask boundaryEvent = getExecutableTaskFrom(executableTaskNodeId,
                                    processInstanceId,
                                    boundaryEventDefinition.id(),
                                    processDefinitionId,
                                    AttachedTaskType.EXECUTABLE_TASK,
                                    ((InterruptiveTimerEventDefinition)boundaryEventDefinition).duration(),
                                    flowNodeDefinitionId);

                            nextExecutableTasks.add(boundaryEvent);
                            boundaryEvents.add(new AttachedEventReference(boundaryEvent.id(), boundaryEventDefinition.id()));
                        });
            }

            ExecutableTask executableTask = ExecutableTask.builder()
                    .id(executableTaskNodeId)
                    .processDefinitionId(processDefinitionId)
                    .taskDefinitionId(flowNodeDefinitionId)
                    .processInstanceId(processInstanceId)
                    .boundaryEvents(boundaryEvents)
                    .build();

            nextExecutableTasks.add(executableTask);
        }
    }
}
