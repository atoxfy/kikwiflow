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

package io.kikwiflow.management.controller.processinstance;

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.exception.NotImplementedException;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.spring.rest.api.query.ProcessInstanceQueryRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(QueryRepository.class)
public class ProcessInstanceQueryController implements ProcessInstanceQueryRestApi {

    private final QueryRepository queryRepository;

    public ProcessInstanceQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public CountResponse count(String processDefinitionId) {
        if(processDefinitionId != null){
            long count = queryRepository.countProcessInstancesByProcessDefinition(processDefinitionId);
            return new CountResponse(count);

        }

        throw new NotImplementedException("Not implemented");
    }

    @Override
    public ProcessInstance findProcessInstanceById(String id) {
        return queryRepository.findProcessInstanceById(id)
                .orElseThrow(() -> new NotFoundException("Process Instance Not Found"));
    }

    @Override
    public List<ProcessInstance> findAll(List<String> ids, String processDefinitionId, String tenantId) {
        if(ids != null && !ids.isEmpty()){
            return queryRepository.findProcessInstancesByIdIn(ids);
        }else if(processDefinitionId != null && tenantId != null){
            return queryRepository.findProcessInstanceByProcessDefinitionId(processDefinitionId, tenantId);
        }

        throw new NotImplementedException("");
    }
}
