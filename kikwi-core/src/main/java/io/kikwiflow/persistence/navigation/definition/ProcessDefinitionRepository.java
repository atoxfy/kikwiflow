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
package io.kikwiflow.persistence.navigation.definition;

import io.kikwiflow.bpmn.model.ProcessDefinitionDeploy;
import io.kikwiflow.model.bpmn.ProcessDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Temporary repository to emulate persistence logic
 */
public class ProcessDefinitionRepository {
    private Map<String, ProcessDefinition> processDefinitionMap = new HashMap<String, ProcessDefinition>();
    private ProcessDefinitionHistoryRepository processDefinitionHistoryRepository = new ProcessDefinitionHistoryRepository();

    private ProcessDefinition mapToEntity(ProcessDefinitionDeploy processDefinitionDeploy){
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setKey(processDefinitionDeploy.getKey());
        processDefinition.setName(processDefinitionDeploy.getName());
        processDefinition.setFlowNodes(processDefinitionDeploy.getFlowNodes());
        return processDefinition;
    }

    public ProcessDefinition save(ProcessDefinitionDeploy processDefinitionDeploy){
        String key = processDefinitionDeploy.getKey();

        ProcessDefinition processDefinition = mapToEntity(processDefinitionDeploy);
        ProcessDefinition lastProcessDefinition = processDefinitionMap.get(key);

        //TODO separar responsabilidades
        if(lastProcessDefinition == null){
            processDefinition.setVersion(1);
        }else {
            processDefinition.setVersion(lastProcessDefinition.getVersion() + 1);
        }

        processDefinition.setId(UUID.randomUUID().toString());
        this.processDefinitionMap.put(processDefinitionDeploy.getKey(), processDefinition);
        this.processDefinitionHistoryRepository.save(processDefinition);
        return processDefinition;
    }

    public ProcessDefinition findByKey(String processDefinitionKey){
        return processDefinitionMap.get(processDefinitionKey);
    }
}
