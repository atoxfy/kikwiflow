/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.execution;

import io.kikwiflow.exception.ProcessErrorException; // Import da nova exceção
import io.kikwiflow.execution.api.RetryPolicyEvaluator;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FailureHandler {

    private final KikwiEngineRepository repository;
    private final RetryPolicyEvaluator policyEvaluator;

    public FailureHandler(KikwiEngineRepository repository, RetryPolicyEvaluator policyEvaluator) {
        this.repository = repository;
        this.policyEvaluator = policyEvaluator;
    }

    public void handleFailure(ExecutableTask task, Exception exception) {
        List<ExecutableTask> tasksToUpdate = new ArrayList<>();
        List<Incident> incidentsToCreate = new ArrayList<>();
        RetryPolicy retryPolicy = task.retryPolicy();

        RetryPolicyEvaluator.RetryEvaluationResult evaluation = policyEvaluator.evaluate(task, exception, retryPolicy);

        Throwable rootCause = exception.getCause() != null ? exception.getCause() : exception;
        boolean isUnhandledBusinessError = rootCause instanceof ProcessErrorException;

        String errorMessage = rootCause.getMessage() != null
                ? rootCause.getMessage()
                : rootCause.getClass().getSimpleName();

        long currentExecutions = task.executions() != null ? task.executions() : 0L;
        long nextExecutionCount = currentExecutions + 1;

        if (!evaluation.shouldCreateIncident() && !isUnhandledBusinessError) {
            ExecutableTask updatedTask = task.toBuilder()
                    .retries(evaluation.retriesLeft())
                    .executions(nextExecutionCount)
                    .dueDate(evaluation.nextDueDate())
                    .status(ExecutableTaskStatus.PENDING)
                    .error(errorMessage)
                    .executorId(null)
                    .build();

            tasksToUpdate.add(updatedTask);
        } else {
            ExecutableTask failedTask = task.toBuilder()
                    .retries(0L)
                    .executions(nextExecutionCount)
                    .status(ExecutableTaskStatus.ERROR)
                    .error(errorMessage)
                    .executorId(null)
                    .build();

            tasksToUpdate.add(failedTask);

            Incident incident = new Incident(
                    UUID.randomUUID().toString(),
                    isUnhandledBusinessError ? "UNHANDLED_BUSINESS_ERROR" : "FAILED_JOB",
                    errorMessage,
                    getStackTrace(exception),
                    task.processDefinitionId(),
                    task.processInstanceId(),
                    task.id(),
                    Instant.now(),
                    IncidentStatus.OPEN,
                    task.taskDefinitionId()
            );

            incidentsToCreate.add(incident);
        }

        UnitOfWork uow = new UnitOfWork(
                null, null, null, null, null, null,
                tasksToUpdate, null, null, incidentsToCreate,
                null, null, null, null, null
        );

        repository.commitWork(uow);
    }

    public String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}