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

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                .build();

        this.processInstanceCollection.put(instanceToSave.id(), instanceToSave);
        return instanceToSave;
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        findProcessInstanceById(processInstanceId)
                .ifPresent(processInstance -> {
                    ProcessInstance instanceToSave = ProcessInstance.builder()
                            .id(processInstance.id())
                            .businessKey(processInstance.businessKey())
                            .processDefinitionId(processInstance.processDefinitionId())
                            .status(processInstance.status())
                            .endedAt(processInstance.endedAt())
                            .variables(variables)
                            .startedAt(processInstance.startedAt())
                            .build();

                    saveProcessInstance(instanceToSave);
                });
    }

    @Override
    public ExecutableTask createExecutableTask(ExecutableTask executableTask) {
        ExecutableTask executableTaskToSave = ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
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
                .build();

        this.executableTaskCollection.put(executableTaskToSave.id(), executableTaskToSave);
        return executableTaskToSave;
    }

    @Override
    public ExternalTask createExternalTask(ExternalTask task) {
        ExternalTask externalTask = ExternalTask.builder()
                .assignee(task.assignee())
                .createdAt(task.createdAt())
                .description(task.description())
                .id(UUID.randomUUID().toString())
                .name(task.name())
                .processDefinitionId(task.processDefinitionId())
                .taskDefinitionId(task.taskDefinitionId())
                .status(task.status())
                .topicName(task.topicName())
                .processInstanceId(task.processInstanceId())
                .build();

        this.externalTaskCollection.put(externalTask.id(), externalTask);
        return externalTask;
    }

    @Override
    public Optional<ExternalTask> completeExternalTask(String externalTaskId) {
        return Optional.empty();
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
                .defaultStartPoint(processDefinition.defaultStartPoint())
                .flowNodes(processDefinition.flowNodes())
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
    public ProcessInstance updateProcessInstance(ProcessInstance processInstance) {
        this.processInstanceCollection.put(processInstance.id(), processInstance);
        return processInstance;
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        this.processInstanceCollection.remove(processInstanceId);
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

        if (unitOfWork.tasksToDelete() != null) {
            unitOfWork.tasksToDelete().forEach(externalTaskCollection::remove);
        }

        if(unitOfWork.events() != null){
            this.outboxEventQueue.addAll(unitOfWork.events());
        }
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        return Optional.empty();
    }
}
