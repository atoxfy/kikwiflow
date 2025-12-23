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

        if (retriesLeft > 0) {
            handleRetry(task, exception, retriesLeft);
        } else {
            handleIncident(task, exception);
        }
    }

    private void handleRetry(ExecutableTask task, Exception e, long retriesLeft) {
        // Implementação simplificada: Calcula novo DueDate (ex: +2 minutos)
        // O ideal é ler a estratégia de backoff da definição do processo
        Instant nextRetry = Instant.now().plus(1, ChronoUnit.MINUTES);

        // AQUI: Você precisa de um método no repositório para atualização atômica de tarefa
        // Não usamos UnitOfWork aqui para não deletar/recriar a task, apenas atualizar campos
        repository.updateExecutableTaskRetries(
                task.id(),
                retriesLeft,
                nextRetry,
                e.getMessage(),
                ExecutableTaskStatus.PENDING // Volta para PENDING para o Acquirer pegar depois
        );
    }

    private void handleIncident(ExecutableTask task, Exception e) {
        // 1. Cria o Incidente
        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                "FAILED_JOB",
                e.getMessage(),
                getStackTrace(e),
                task.processDefinitionId(),
                task.processInstanceId(),
                task.id(),
                Instant.now(),
                IncidentStatus.OPEN
        );

        // 2. Atualiza a Task para FAILED (para o Acquirer parar de pegar)
        repository.updateExecutableTaskStatus(task.id(), ExecutableTaskStatus.ERROR, e.getMessage());

        // 3. Salva o Incidente via UnitOfWork
        UnitOfWork uow = new UnitOfWork(
                null,
                null,
                null,
                null, null, null, null,
                List.of(incident) ,
                null
        );
        repository.commitWork(uow);
    }

    private String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString(); // Limitar caracteres se necessário
    }
}