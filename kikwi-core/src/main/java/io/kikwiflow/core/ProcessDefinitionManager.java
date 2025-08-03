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
package io.kikwiflow.core;

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.deploy.ProcessDefinitionDeploy;
import io.kikwiflow.persistence.navigation.definition.ProcessDefinitionCache;
import io.kikwiflow.persistence.navigation.definition.ProcessDefinitionRepository;

import java.io.InputStream;

public class ProcessDefinitionManager {
    private final BpmnParser bpmnParser;
    private final ProcessDefinitionRepository processDefinitionRepository;
    private final ProcessDefinitionCache processDefinitionCache;

    public ProcessDefinitionManager(){
        this.bpmnParser = new DefaultBpmnParser();
        this.processDefinitionRepository = new ProcessDefinitionRepository();
        this.processDefinitionCache = new ProcessDefinitionCache();
    }

    public void deploy(InputStream inputStream) throws Exception {
        ProcessDefinitionDeploy processDefinitionDeploy = bpmnParser.parse(inputStream);
        processDefinitionRepository.save(processDefinitionDeploy);
    }

    /**
     * Get processDefinition by processDefinitionKey if cached or else get in db and save in cache.
     * @param processDefinitionKey
     * @return processDefinition
     * @throws Exception if the requested processDefinition doesn't exist in cache or db
     */
    public ProcessDefinition getByKey(String processDefinitionKey){
        ProcessDefinition processDefinition = processDefinitionCache.findByKey(processDefinitionKey);

        if(processDefinition != null){
            return processDefinition;
        }


        processDefinition = processDefinitionRepository.findByKey(processDefinitionKey);
        if(processDefinition == null){
            throw new RuntimeException("Não existe processo salvo: " + processDefinitionKey);
        }

        processDefinitionCache.add(processDefinition);
        return processDefinition;
    }
}
