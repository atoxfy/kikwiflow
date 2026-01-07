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

package io.kikwiflow.management.controller.externaltask;

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.exception.NotImplementedException;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.spring.rest.api.query.ExternalTaskQueryRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(QueryRepository.class)
public class ExternalTaskQueryController implements ExternalTaskQueryRestApi {

    private final QueryRepository queryRepository;

    public ExternalTaskQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public CountResponse count(String processDefinitionId, String tenantId, String assignee, String processInstanceId, List<String> processInstanceIdIn, String taskDefinitionId, List<String> tenantIds) {
        ExternalTaskQuery query = queryRepository.createExternalTaskQuery();
        if(processDefinitionId != null){
            query.processDefinitionId(processDefinitionId);
        }

        if(tenantId != null){
            query.tenantId(tenantId);
        }

        if(assignee != null){
            query.assignee(assignee);
        }

        if(processInstanceId != null){
            throw new NotImplementedException("");
        }

        if(processInstanceIdIn != null){
            throw new NotImplementedException("");
        }

        if(taskDefinitionId != null){
            throw new NotImplementedException("");
        }

        if(tenantIds != null){
            throw new NotImplementedException("");
        }


        long count = query.count();
        return new CountResponse(count);
    }

    @Override
    public ExternalTask findExternalTaskById(String id) {
        return queryRepository.findExternalTaskById(id)
                .orElseThrow(() -> new NotFoundException(""));
    }

    @Override
    public List<ExternalTask> findAll(String processDefinitionId, String tenantId, String assignee, String processInstanceId, List<String> processInstanceIdIn, String taskDefinitionId, List<String> tenantIds) {
        ExternalTaskQuery query = queryRepository.createExternalTaskQuery();
        if(processDefinitionId != null){
            query.processDefinitionId(processDefinitionId);
        }

        if(tenantId != null){
            query.tenantId(tenantId);
        }

        if(assignee != null){
            query.assignee(assignee);
        }

        if(processInstanceId != null){
            throw new NotImplementedException("");
        }

        if(processInstanceIdIn != null){
            throw new NotImplementedException("");
        }

        if(taskDefinitionId != null){
            throw new NotImplementedException("");
        }

        if(tenantIds != null){
            throw new NotImplementedException("");
        }

        return query.list();
    }
}
