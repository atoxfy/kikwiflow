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

package io.kikwiflow.spring.rest.api.command;

import io.kikwiflow.api.command.ProcessInstanceOperationsApi;
import io.kikwiflow.api.dto.ProcessInstanceStartRequest;
import io.kikwiflow.api.dto.SetVariablesRequest;
import io.kikwiflow.model.execution.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("${kikwiflow.api.base-path:/engine/api/v1}/process-instances")
public interface ProcessInstanceOperationsRestApi extends ProcessInstanceOperationsApi {

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProcessInstance start(@RequestBody ProcessInstanceStartRequest processInstanceStartRequest);

    @Override
    @PutMapping("{id}/variables")
    @ResponseStatus(HttpStatus.OK)
    ProcessInstance setVariables(@PathVariable String id, @RequestBody SetVariablesRequest setVariablesRequest);

    @Override
    @PutMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteInstance(String processInstanceId);

}
