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

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
public class FailureHandler {

    private final KikwiEngineRepository repository;

    public FailureHandler(KikwiEngineRepository repository) {
        this.repository = repository;
    }

    public void handleFailure(ExecutableTask task, Exception exception) {
        long retriesLeft = task.retries() - 1;

        List<ExecutableTask> tasksToUpdate = new ArrayList<>();
        List<Incident> incidentsToCreate = new ArrayList<>();

        if (retriesLeft > 0) {
            // Retentativa: Joga para o futuro e volta o status para PENDING
            Instant nextRetry = Instant.now().plus(1, ChronoUnit.MINUTES);

            ExecutableTask updatedTask = task.toBuilder()
                    .retries(retriesLeft)
                    .dueDate(nextRetry)
                    .status(ExecutableTaskStatus.PENDING)
                    .error(exception.getMessage())
                    .executorId(null) // Libera o lock
                    .build();

            tasksToUpdate.add(updatedTask);
        } else {
            // Esgotaram os retries: A tarefa morre em ERROR e o Incidente nasce
            ExecutableTask failedTask = task.toBuilder()
                    .retries(0L)
                    .status(ExecutableTaskStatus.ERROR)
                    .error(exception.getMessage())
                    .executorId(null)
                    .build();

            tasksToUpdate.add(failedTask);

            Incident incident = new Incident(
                    UUID.randomUUID().toString(),
                    "FAILED_JOB",
                    exception.getMessage(),
                    getStackTrace(exception),
                    task.processDefinitionId(),
                    task.processInstanceId(),
                    task.id(),
                    Instant.now(),
                    IncidentStatus.OPEN
            );
            incidentsToCreate.add(incident);
        }

        // Commita as atualizações em uma única transação atômica
        UnitOfWork uow = new UnitOfWork(
                null,
                null,
                null,
                null,
                null,
                null,
                tasksToUpdate,
                null,
                null,
                incidentsToCreate,
                null
        );
        repository.commitWork(uow);
    }

    private void handleRetry(ExecutableTask task, Exception e, long retriesLeft) {
        Instant nextRetry = Instant.now().plus(1, ChronoUnit.MINUTES);

        /*repository.updateExecutableTaskRetries(
                task.id(),
                retriesLeft,
                nextRetry,
                e.getMessage(),
                ExecutableTaskStatus.PENDING // Volta para PENDING para o Acquirer pegar depois
        );*/
    }

    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString(); // Limitar caracteres se necessário
    }
}