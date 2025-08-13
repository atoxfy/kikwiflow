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

import io.kikwiflow.persistence.api.data.ExecutableTaskEntity;
import io.kikwiflow.persistence.api.data.ProcessDefinitionEntity;
import io.kikwiflow.persistence.api.data.ProcessInstanceEntity;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

public class InMemoryKikwiEngineRepository implements KikwiEngineRepository {

    private Map<String, ProcessInstanceEntity> processInstanceCollection = new HashMap<>();
    private Map<String, ExecutableTaskEntity> executableTaskCollection = new HashMap<>();
    private Map<String, ProcessDefinitionEntity> processDefinitionCollection = new HashMap<String, ProcessDefinitionEntity>();
    private Map<String, Map<Integer, ProcessDefinitionEntity>> processDefinitionHistoryCollection= new HashMap<String, Map<Integer, ProcessDefinitionEntity>>();
    private final Queue<OutboxEventEntity> outboxEventQueue;

    public InMemoryKikwiEngineRepository(Queue<OutboxEventEntity> outboxEventQueue){
        this.outboxEventQueue = outboxEventQueue;
    }

    public void reset(){
        processDefinitionCollection.clear();
        executableTaskCollection.clear();
        processDefinitionCollection.clear();
        outboxEventQueue.clear();
        processDefinitionHistoryCollection.clear();
    }

    @Override
    public ProcessInstanceEntity saveProcessInstance(ProcessInstanceEntity instance) {
        instance.setId(UUID.randomUUID().toString());
        this.processInstanceCollection.put(instance.getId(), instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstanceEntity> findProcessInstanceById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        findProcessInstanceById(processInstanceId)
                .ifPresent(processInstance -> processInstance.setVariables(variables));
    }

    @Override
    public ExecutableTaskEntity createExecutableTask(ExecutableTaskEntity executableTask) {
        executableTask.setId(UUID.randomUUID().toString());
        this.executableTaskCollection.put(executableTask.getId(), executableTask);
        return executableTask;
    }


    @Override
    public ProcessDefinitionEntity saveProcessDefinition(ProcessDefinitionEntity processDefinition){
        String key = processDefinition.getKey();
        ProcessDefinitionEntity lastProcessDefinition = processDefinitionCollection.get(key);
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
    public Optional<ProcessDefinitionEntity> findProcessDefinitionByKey(String processDefinitionKey){
        return Optional.ofNullable(processDefinitionCollection.get(processDefinitionKey));
    }

    public void addToHistory(ProcessDefinitionEntity processDefinition){
        String key = processDefinition.getKey();
        Map<Integer, ProcessDefinitionEntity> processDefinitionVersionMap = processDefinitionHistoryCollection.get(key);
        if(null == processDefinitionVersionMap){
            processDefinitionVersionMap = new HashMap<Integer, ProcessDefinitionEntity>();
        }

        processDefinitionVersionMap.put(processDefinition.getVersion(), processDefinition);
        processDefinitionHistoryCollection.put(key, processDefinitionVersionMap);
    }

    @Override
    public ProcessInstanceEntity updateProcessInstance(ProcessInstanceEntity processInstance) {
        this.processInstanceCollection.put(processInstance.getId(), processInstance);
        return processInstance;
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        this.processInstanceCollection.remove(processInstanceId);
    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        this.processInstanceCollection.put(unitOfWork.instanceToUpdate().getId(), unitOfWork.instanceToUpdate());
        this.outboxEventQueue.addAll(unitOfWork.events());
    }
}
