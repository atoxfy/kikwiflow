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

import io.kikwiflow.api.command.ExternalTaskOperationsApi;
import io.kikwiflow.api.dto.CompleteExternalTaskRequest;
import io.kikwiflow.model.execution.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("${kikwiflow.api.path:/engine/api/v1}/external-tasks")
public interface ExternalTaskOperationsRestApi extends ExternalTaskOperationsApi {

    @Override
    @PutMapping("{id}/claim/{assignee}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void claim(@PathVariable String id, @PathVariable String assignee);

    @Override
    @PutMapping("{id}/unclaim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unclaim(@PathVariable String id);

    @Override
    @PostMapping("{id}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ProcessInstance completeExternalTask(@PathVariable String id, @RequestBody CompleteExternalTaskRequest completeExternalTaskRequest);

}
