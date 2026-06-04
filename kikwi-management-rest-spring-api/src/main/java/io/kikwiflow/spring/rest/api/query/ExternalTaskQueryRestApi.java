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

package io.kikwiflow.spring.rest.api.query;

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.api.query.ExternalTaskQueryApi;
import io.kikwiflow.model.execution.node.ExternalTask;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RequestMapping("/external-tasks")
public interface ExternalTaskQueryRestApi extends ExternalTaskQueryApi {

    @Override
    @GetMapping("count")
    @ResponseStatus(HttpStatus.OK)
    CountResponse count(@RequestParam(value = "process-definition-id", required = false) String processDefinitionId,
                                                   @RequestParam(value = "tenant-id", required = false) String tenantId,
                                                   @RequestParam(value = "assignee", required = false) String assignee,
                                                   @RequestParam(value = "process-instance-id", required = false) String processInstanceId,
                                                   @RequestParam(value = "process-instance-id-in", required = false) List<String> processInstanceIdIn,
                                                   @RequestParam(value = "task-definition-id", required = false) String taskDefinitionId,
                                                   @RequestParam(value = "tenant-ids", required = false) List<String> tenantIds);

    @Override
    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    ExternalTask findExternalTaskById(@PathVariable(value = "id") String id);


    @Override
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    List<ExternalTask> findAll(@RequestParam(value = "process-definition-id", required = false) String processDefinitionId,
                               @RequestParam(value = "tenant-id", required = false) String tenantId,
                               @RequestParam(value = "assignee", required = false) String assignee,
                               @RequestParam(value = "process-instance-id", required = false) String processInstanceId,
                               @RequestParam(value = "process-instance-id-in", required = false) List<String> processInstanceIdIn,
                               @RequestParam(value = "task-definition-id", required = false) String taskDefinitionId,
                               @RequestParam(value = "tenant-ids", required = false) List<String> tenantIds);


}
