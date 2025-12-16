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
package io.kikwiflow.persistence;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

public class InMemoryKikwiEngineRepository implements KikwiEngineRepository {

    private final Map<String, ProcessInstance> processInstanceCollection = new HashMap<>();
    private final Map<String, ExecutableTask> executableTaskCollection = new HashMap<>();
    private final Map<String, ExternalTask> externalTaskCollection = new HashMap<>();
    private final Map<String, ProcessDefinition> processDefinitionCollection = new HashMap<String, ProcessDefinition>();
    private final Map<String, Map<Integer, ProcessDefinition>> processDefinitionHistoryCollection= new HashMap<String, Map<Integer, ProcessDefinition>>();
    private final Queue<OutboxEventEntity> outboxEventQueue;

    public InMemoryKikwiEngineRepository(Queue<OutboxEventEntity> outboxEventQueue){
        this.outboxEventQueue = outboxEventQueue;
    }

    public void reset(){
        processDefinitionCollection.clear();
        executableTaskCollection.clear();
        externalTaskCollection.clear();
        processDefinitionCollection.clear();
        outboxEventQueue.clear();
        processDefinitionHistoryCollection.clear();
    }

    @Override
    public ProcessInstance saveProcessInstance(ProcessInstance instance) {

        ProcessInstance instanceToSave = ProcessInstance.builder()
                .id(null != instance.id() ? instance.id() : UUID.randomUUID().toString())
                .businessKey(instance.businessKey())
                .processDefinitionId(instance.processDefinitionId())
                .status(instance.status())
                .endedAt(instance.endedAt())
                .variables(instance.variables())
                .startedAt(instance.startedAt())
                .tenantId(instance.tenantId())
                .businessValue(instance.businessValue())
                .build();

        this.processInstanceCollection.put(instanceToSave.id(), instanceToSave);
        return instanceToSave;
    }

    @Override
    public List<ProcessDefinition> findAProcessDefinitionsByParams(String key) {
        return List.of();
    }

    @Override
    public List<ProcessDefinition> findAllProcessDefinitions() {
        return List.of();
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public List<ProcessInstance> findProcessInstancesByIdIn(List<String> ids) {
        return List.of();
    }


    public ExecutableTask createExecutableTask(ExecutableTask executableTask) {
        ExecutableTask executableTaskToSave = ExecutableTask.builder()
                .id(executableTask.id())
                .createdAt(executableTask.createdAt())
                .executions(executableTask.executions())
                .acquiredAt(executableTask.acquiredAt())
                .description(executableTask.description())
                .error(executableTask.error())
                .executorId(executableTask.executorId())
                .name(executableTask.name())
                .taskDefinitionId(executableTask.taskDefinitionId())
                .processDefinitionId(executableTask.processDefinitionId())
                .processInstanceId(executableTask.processInstanceId())
                .retries(executableTask.retries())
                .status(executableTask.status())
                .attachedToRefType(executableTask.attachedToRefType())
                .attachedToRefId(executableTask.attachedToRefId())
                .dueDate(executableTask.dueDate())
                .boundaryEvents(executableTask.boundaryEvents())
                .build();

        this.executableTaskCollection.put(executableTaskToSave.id(), executableTaskToSave);
        return executableTaskToSave;
    }

    public ExternalTask createExternalTask(ExternalTask task) {
        ExternalTask externalTask = ExternalTask.builder()
                .assignee(task.assignee())
                .createdAt(task.createdAt())
                .description(task.description())
                .id(task.id())
                .name(task.name())
                .processDefinitionId(task.processDefinitionId())
                .taskDefinitionId(task.taskDefinitionId())
                .status(task.status())
                .topicName(task.topicName())
                .processInstanceId(task.processInstanceId())
                .boundaryEvents(task.boundaryEvents())
                .tenantId(task.tenantId())
                .build();

        this.externalTaskCollection.put(externalTask.id(), externalTask);
        return externalTask;
    }

    @Override
    public Optional<ExternalTask> findExternalTaskById(String externalTaskId) {
        return Optional.ofNullable(externalTaskCollection.get(externalTaskId));
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {
        return externalTaskCollection.values().stream()
            .filter(task -> processInstanceId.equals(task.processInstanceId()))
            .toList();
    }

    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinition){
        String key = processDefinition.key();
        ProcessDefinition lastProcessDefinition = processDefinitionCollection.get(key);
        Integer version = 0;

        //TODO separar responsabilidades
        if(lastProcessDefinition != null){
            version = lastProcessDefinition.version();
        }

        ProcessDefinition processDefinitionToSave = ProcessDefinition.builder()
                .id(UUID.randomUUID().toString())
                .version(version)
                .key(key)
                .name(processDefinition.name())
                .defaultStartPoint(processDefinition.defaultStartPoint())
                .flowNodes(processDefinition.flowNodes())
                .description(processDefinition.description())
                .build();

        this.processDefinitionCollection.put(key, processDefinitionToSave);
        this.addToHistory(processDefinitionToSave);
        return processDefinitionToSave;
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey){
        return Optional.ofNullable(processDefinitionCollection.get(processDefinitionKey));
    }

    public void addToHistory(ProcessDefinition processDefinition){
        String key = processDefinition.key();
        Map<Integer, ProcessDefinition> processDefinitionVersionMap = processDefinitionHistoryCollection.get(key);
        if(null == processDefinitionVersionMap){
            processDefinitionVersionMap = new HashMap<Integer, ProcessDefinition>();
        }

        processDefinitionVersionMap.put(processDefinition.version(), processDefinition);
        processDefinitionHistoryCollection.put(key, processDefinitionVersionMap);
    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {

        //TODO adjust it /!\
        if(unitOfWork.instanceToDelete() != null){
            this.processInstanceCollection.remove(unitOfWork.instanceToDelete().id());
        }

        if(unitOfWork.instanceToUpdate() != null){
          this.saveProcessInstance(unitOfWork.instanceToUpdate());
        }

        if(unitOfWork.executableTasksToCreate() != null){
            unitOfWork.executableTasksToCreate().forEach(this::createExecutableTask);
        }

        if(unitOfWork.externalTasksToCreate() != null){
            unitOfWork.externalTasksToCreate().forEach(this::createExternalTask);
        }

        if (unitOfWork.externalTasksToDelete() != null) {
            unitOfWork.externalTasksToDelete().forEach(externalTaskCollection::remove);
        }

        if (unitOfWork.executableTasksToDelete() != null) {
            unitOfWork.executableTasksToDelete().forEach(executableTaskCollection::remove);
        }

        if(unitOfWork.events() != null){
            this.outboxEventQueue.addAll(unitOfWork.events());
        }
    }

    @Override
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId) {
        List<ExecutableTask> candidates = this.executableTaskCollection.values().stream()
                .filter(task -> task.status() == ExecutableTaskStatus.PENDING)
                .filter(task -> task.dueDate() == null || !task.dueDate().isAfter(now))
                .sorted(Comparator.comparing(task -> task.dueDate() == null ? Instant.MIN : task.dueDate()))
                .limit(limit)
                .toList();

        List<ExecutableTask> lockedTasks = new ArrayList<>();
        for (ExecutableTask candidate : candidates){
            ExecutableTask lockedTask = candidate.toBuilder()
                    .status(ExecutableTaskStatus.EXECUTING)
                    .executorId(workerId)
                    .acquiredAt(Instant.now())
                    .build();
            this.executableTaskCollection.put(candidate.id(), lockedTask);
            lockedTasks.add(lockedTask);
        }

        return lockedTasks;
    }

    @Override
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables) {
        //TODO
        return null;
    }

    @Override
    public void claim(String externalTaskId, String assignee) {
        //TODO
    }

    @Override
    public void unclaim(String externalTaskId) {
        //TODO
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {

    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        //TODO
        return Optional.empty();
    }

    @Override
    public Optional<ExecutableTask> findExecutableTaskById(String executableTaskId) {
        return Optional.ofNullable(this.executableTaskCollection.get(executableTaskId));
    }

    @Override
    public Optional<ExecutableTask> findAndGetFirstPendingExecutableTask(String id) {
        return executableTaskCollection.values()
                .stream()
                .filter(task -> task.status() == ExecutableTaskStatus.PENDING)
                .findFirst();
    }

    @Override
    public List<ProcessInstance> findProcessInstanceByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return processInstanceCollection.values()
                .stream()
                .filter(p -> p.processDefinitionId().equals(processDefinitionId) && Objects.equals(tenantId, p.tenantId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> t.processDefinitionId().equals(processDefinitionId) && Objects.equals(tenantId, t.tenantId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId) {
        return List.of();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, List<String> tenantIds) {
        return List.of();
    }

    @Override
    public List<ExternalTask> findExternalTasksByAssignee(String assignee, String tenantId) {
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> Objects.equals(assignee, t.assignee()) && Objects.equals(tenantId, t.tenantId()))
                .toList();
    }

    @Override
    public ExternalTaskQuery createExternalTaskQuery() {
        return null;
    }

    @Override
    public void ensureIndexes() {

    }
}
