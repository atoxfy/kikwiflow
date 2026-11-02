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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.api.dto.ExternalTaskWorkerRequests;
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.spring.rest.api.command.ExternalTaskWorkerRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;

@KikwiRestController
@ConditionalOnBean(KikwiflowEngine.class)
public class ExternalTaskWorkerController implements ExternalTaskWorkerRestApi {

    private final KikwiflowEngine engine;

    public ExternalTaskWorkerController(KikwiflowEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<LockedExternalTask> fetchAndLock(ExternalTaskWorkerRequests.FetchAndLock request, IdentityContext identityContext) {
        return engine.fetchAndLock(request.workerId(), request.topics(),
                request.maxTasks() != null ? request.maxTasks() : 1,
                parseDuration("lockDuration", request.lockDuration()), identityContext);
    }

    @Override
    public ExternalTask extendLock(String id, ExternalTaskWorkerRequests.ExtendLock request, IdentityContext identityContext) {
        return engine.extendLock(id, request.workerId(), parseDuration("lockDuration", request.lockDuration()), identityContext);
    }

    @Override
    public void handleFailure(String id, ExternalTaskWorkerRequests.Failure request, IdentityContext identityContext) {
        engine.handleFailure(id, request.workerId(), request.errorMessage(), request.errorDetails(),
                request.retries() != null ? request.retries() : 0,
                request.retryTimeout() != null ? parseDuration("retryTimeout", request.retryTimeout()) : null,
                identityContext);
    }

    @Override
    public ProcessInstance throwBusinessError(String id, ExternalTaskWorkerRequests.BusinessError request,
                                              IdentityContext identityContext) {
        return engine.throwBusinessError(id, request.workerId(), request.errorCode(), request.errorMessage(),
                request.variables(), identityContext);
    }

    @Override
    public void reportActivities(String id, ExternalTaskWorkerRequests.ReportActivities request, IdentityContext identityContext) {
        engine.reportActivities(id, request.workerId(), request.activities(), identityContext);
    }

    private static Duration parseDuration(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' é obrigatório (duração ISO-8601, ex.: PT5M).");
        }
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + field + "' não é uma duração ISO-8601 válida: " + value);
        }
    }
}
