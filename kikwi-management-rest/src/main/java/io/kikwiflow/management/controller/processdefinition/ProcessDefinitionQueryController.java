/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.management.controller.processdefinition;

import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.spring.rest.api.query.ProcessDefinitionQueryRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(QueryRepository.class)
public class ProcessDefinitionQueryController implements ProcessDefinitionQueryRestApi {

    private final QueryRepository queryRepository;

    public ProcessDefinitionQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public List<ProcessDefinition> findAll(String key) {
        if(key != null){
            return queryRepository.findAProcessDefinitionsByParams(key);
        }

        return queryRepository.findAllProcessDefinitions();
    }

    @Override
    public ProcessDefinition findProcessDefinitionByKey(String processDefinitionKey) {
        return queryRepository.findProcessDefinitionByKey(processDefinitionKey)
                .orElseThrow(() -> new NotFoundException("ProcessDefinition not found with key " + processDefinitionKey));
    }

    @Override
    public ProcessDefinition findProcessDefinitionById(String id) {
        return queryRepository.findProcessDefinitionById(id)
                .orElseThrow(() -> new NotFoundException("ProcessDefinition not found with id " + id));
    }
}
