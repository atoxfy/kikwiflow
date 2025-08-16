/*
 * Copyright Atoxfy and/or licensed to Atoxfy
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
        instance.setId(UUID.randomUUID().toString());
        this.processInstanceCollection.put(instance.getId(), instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        findProcessInstanceById(processInstanceId)
                .ifPresent(processInstance -> processInstance.setVariables(variables));
    }

    @Override
    public ExecutableTask createExecutableTask(ExecutableTask executableTask) {
        executableTask.setId(UUID.randomUUID().toString());
        this.executableTaskCollection.put(executableTask.getId(), executableTask);
        return executableTask;
    }

    @Override
    public ExternalTask createExternalTask(ExternalTask task) {
        task.setId(UUID.randomUUID().toString());
        this.externalTaskCollection.put(task.getId(), task);
        return task;
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {
        return externalTaskCollection.values().stream()
            .filter(task -> processInstanceId.equals(task.getProcessInstanceId()))
            .toList();
    }


    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinition){
        String key = processDefinition.getKey();
        ProcessDefinition lastProcessDefinition = processDefinitionCollection.get(key);
        Integer version = 0;
        //TODO separar responsabilidades
        if(lastProcessDefinition != null){
            version = lastProcessDefinition.getVersion();
        }

        final String id = UUID.randomUUID().toString();
        processDefinition.setId(id);
        processDefinition.setVersion(version);

        this.processDefinitionCollection.put(key, processDefinition);
        this.addToHistory(processDefinition);
        return processDefinition;
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey){
        return Optional.ofNullable(processDefinitionCollection.get(processDefinitionKey));
    }

    public void addToHistory(ProcessDefinition processDefinition){
        String key = processDefinition.getKey();
        Map<Integer, ProcessDefinition> processDefinitionVersionMap = processDefinitionHistoryCollection.get(key);
        if(null == processDefinitionVersionMap){
            processDefinitionVersionMap = new HashMap<Integer, ProcessDefinition>();
        }

        processDefinitionVersionMap.put(processDefinition.getVersion(), processDefinition);
        processDefinitionHistoryCollection.put(key, processDefinitionVersionMap);
    }

    @Override
    public ProcessInstance updateProcessInstance(ProcessInstance processInstance) {
        this.processInstanceCollection.put(processInstance.getId(), processInstance);
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
            this.processInstanceCollection.remove(unitOfWork.instanceToDelete().getId());
        }

        if(unitOfWork.instanceToUpdate() != null){
            this.processInstanceCollection.put(unitOfWork.instanceToUpdate().getId(), unitOfWork.instanceToUpdate());
        }

        if(unitOfWork.executableTasksToCreate() != null){
            unitOfWork.executableTasksToCreate().forEach(this::createExecutableTask);
        }

        if(unitOfWork.externalTasksToCreate() != null){
            unitOfWork.externalTasksToCreate().forEach(this::createExternalTask);
        }

        if(unitOfWork.events() != null){
            this.outboxEventQueue.addAll(unitOfWork.events());
        }
    }
}
