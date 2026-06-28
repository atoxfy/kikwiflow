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

package io.kikwiflow.management.service;

import io.kikwiflow.management.dtos.ProcessInstanceSnapshot;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ProcessInstanceSnapshotService {

    private final QueryRepository queryRepository;
    private final ExecutorService executor;

    public ProcessInstanceSnapshotService(QueryRepository queryRepository, ExecutorService executor) {
        this.queryRepository = queryRepository;
        this.executor = executor;
    }

    public ProcessInstanceSnapshot getSnapshot(String processInstanceId) {
        // Dispara as consultas de forma concorrente no executor isolado
        CompletableFuture<Optional<ProcessInstance>> instanceFuture = CompletableFuture.supplyAsync(
                () -> queryRepository.findProcessInstanceById(processInstanceId), executor);

        // NOTA: Certifique-se que o método findExecutableTasksByProcessInstanceId existe no QueryRepository
        CompletableFuture<List<ExecutableTask>> execTasksFuture = CompletableFuture.supplyAsync(
                () -> queryRepository.findExecutableTasksByProcessInstanceId(processInstanceId), executor);

        CompletableFuture<List<ExternalTask>> extTasksFuture = CompletableFuture.supplyAsync(
                () -> queryRepository.findExternalTasksByProcessInstanceId(processInstanceId), executor);

        CompletableFuture<List<Incident>> incidentsFuture = CompletableFuture.supplyAsync(
                () -> queryRepository.findIncidentsByProcessInstanceId(processInstanceId), executor);

        // Barreira de sincronização: Aguarda todos terminarem
        CompletableFuture.allOf(instanceFuture, execTasksFuture, extTasksFuture, incidentsFuture).join();

        // Extrai os resultados
        ProcessInstance instance = instanceFuture.join()
                .orElseThrow(() -> new NotFoundException("Process Instance Not Found: " + processInstanceId));

        return new ProcessInstanceSnapshot(
                instance,
                execTasksFuture.join(),
                extTasksFuture.join(),
                incidentsFuture.join()
        );
    }
}