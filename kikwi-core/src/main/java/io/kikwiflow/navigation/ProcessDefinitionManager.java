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
package io.kikwiflow.navigation;

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.mapper.ProcessDefinitionMapper;
import io.kikwiflow.exception.ProcessDefinitionNotFoundException;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.cache.ProcessDefinitionCache;

import java.io.InputStream;
import java.util.Optional;

public class ProcessDefinitionManager {

    private final BpmnParser bpmnParser;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final ProcessDefinitionCache processDefinitionCache;

    public ProcessDefinitionManager(BpmnParser bpmnParser, KikwiEngineRepository kikwiEngineRepository){
        this.bpmnParser =  bpmnParser;
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.processDefinitionCache = new ProcessDefinitionCache();
    }

    public ProcessDefinition deploy(InputStream inputStream) throws Exception {
        ProcessDefinition processDefinitionDeploy = bpmnParser.parse(inputStream);
        ProcessDefinition processDefinition = kikwiEngineRepository.saveProcessDefinition(ProcessDefinitionMapper.mapToEntity(processDefinitionDeploy));
        return ProcessDefinitionMapper.toSnapshot(processDefinition);
    }

    /**
     * Get processDefinition by processDefinitionKey if cached or else get in db and save in cache.
     * @param processDefinitionKey
     * @return processDefinition
     * @throws Exception if the requested processDefinition doesn't exist in cache or db
     */
    public Optional<ProcessDefinition> getByKey(String processDefinitionKey){
        return processDefinitionCache.findByKey(processDefinitionKey)
                .or(() -> getAndLoadOnCacheByKey(processDefinitionKey));
    }

    public ProcessDefinition getByKeyOrElseThrow(String processDefinitionKey){
        return getByKey(processDefinitionKey)
                .orElseThrow(() -> new ProcessDefinitionNotFoundException("ProcessDefinition not found with key" + processDefinitionKey));
    }


    private Optional<ProcessDefinition> getAndLoadOnCacheByKey(String processDefinitionKey){
        return kikwiEngineRepository.findProcessDefinitionByKey(processDefinitionKey)
                .map(ProcessDefinitionMapper::toSnapshot)
                .map(processDefinitionCache::add);
    }
}
