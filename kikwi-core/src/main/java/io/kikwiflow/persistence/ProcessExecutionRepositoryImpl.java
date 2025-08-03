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

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.task.ServiceTask;
import io.kikwiflow.model.deploy.ProcessDefinitionDeploy;
import io.kikwiflow.model.execution.ExecutableTaskEntity;
import io.kikwiflow.model.execution.ProcessInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

//Temporary, just for logic test
public class ProcessExecutionRepositoryImpl implements ProcessExecutionRepository {

    private Map<String, ProcessInstance> processInstanceCollection = new HashMap<>();
    private Map<String, ExecutableTaskEntity> executableTaskCollection = new HashMap<>();
    private Map<String, ProcessDefinition> processDefinitionCollection = new HashMap<String, ProcessDefinition>();
    private Map<String, Map<Integer, ProcessDefinition>> processDefinitionHistoryCollection= new HashMap<String, Map<Integer, ProcessDefinition>>();


    @Override
    public ProcessInstance save(ProcessInstance instance) {
        instance.setId(UUID.randomUUID().toString());
        this.processInstanceCollection.put(instance.getId(), instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstance> findById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        findById(processInstanceId)
                .ifPresent(processInstance -> processInstance.setVariables(variables));
    }

    @Override
    public ExecutableTaskEntity create(ExecutableTaskEntity executableTask) {
        executableTask.setId(UUID.randomUUID().toString());
        this.executableTaskCollection.put(executableTask.getId(), executableTask);
        return executableTask;
    }

    @Override
    public Optional<ExecutableTaskEntity> acquireNext() {
        return Optional.empty();
    }

    @Override
    public void moveToHistory(ServiceTask completedTask) {

    }

    private ProcessDefinition mapToEntity(ProcessDefinitionDeploy processDefinitionDeploy){
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setKey(processDefinitionDeploy.getKey());
        processDefinition.setName(processDefinitionDeploy.getName());
        processDefinition.setFlowNodes(processDefinitionDeploy.getFlowNodes());
        return processDefinition;
    }

    @Override
    public ProcessDefinition save(ProcessDefinitionDeploy processDefinitionDeploy){
        String key = processDefinitionDeploy.getKey();

        ProcessDefinition processDefinition = mapToEntity(processDefinitionDeploy);
        ProcessDefinition lastProcessDefinition = processDefinitionCollection.get(key);

        //TODO separar responsabilidades
        if(lastProcessDefinition == null){
            processDefinition.setVersion(1);
        }else {
            processDefinition.setVersion(lastProcessDefinition.getVersion() + 1);
        }

        processDefinition.setId(UUID.randomUUID().toString());
        this.processDefinitionCollection.put(processDefinitionDeploy.getKey(), processDefinition);
        this.addToHistory(processDefinition);
        return processDefinition;
    }

    @Override
    public Optional<ProcessDefinition> findByKey(String processDefinitionKey){
        return Optional.ofNullable(processDefinitionCollection.get(processDefinitionKey));
    }

    @Override
    public void addToHistory(ProcessDefinition processDefinition){
        String key = processDefinition.getKey();
        Map<Integer, ProcessDefinition> processDefinitionVersionMap = processDefinitionHistoryCollection.get(key);
        if(null == processDefinitionVersionMap){
            processDefinitionVersionMap = new HashMap<Integer, ProcessDefinition>();
        }

        processDefinitionVersionMap.put(processDefinition.getVersion(), processDefinition);
        processDefinitionHistoryCollection.put(key, processDefinitionVersionMap);
    }
}
