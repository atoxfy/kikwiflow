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

import io.kikwiflow.api.dto.ExternalTaskWorkerRequests;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Protocolo de worker com lease (docs/engine/31, E2/E3). A conclusão é o {@code POST /external-tasks/{id}/complete}
 * de sempre, com {@code workerId} no corpo. Lease que não é do worker: 409; tarefa que sumiu (concluída ou
 * interrompida por um boundary event): 404 — nos dois casos o worker aborta a missão.
 */
@RequestMapping("/external-tasks")
public interface ExternalTaskWorkerRestApi {

    @PostMapping("fetch-and-lock")
    List<LockedExternalTask> fetchAndLock(@RequestBody ExternalTaskWorkerRequests.FetchAndLock request,
                                          IdentityContext identityContext);

    @PostMapping("{id}/extend-lock")
    ExternalTask extendLock(@PathVariable("id") String id, @RequestBody ExternalTaskWorkerRequests.ExtendLock request,
                            IdentityContext identityContext);

    @PostMapping("{id}/failure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void handleFailure(@PathVariable("id") String id, @RequestBody ExternalTaskWorkerRequests.Failure request,
                       IdentityContext identityContext);

    @PostMapping("{id}/bpmn-error")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ProcessInstance throwBusinessError(@PathVariable("id") String id,
                                       @RequestBody ExternalTaskWorkerRequests.BusinessError request,
                                       IdentityContext identityContext);

    @PostMapping("{id}/activities")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportActivities(@PathVariable("id") String id, @RequestBody ExternalTaskWorkerRequests.ReportActivities request,
                          IdentityContext identityContext);
}
