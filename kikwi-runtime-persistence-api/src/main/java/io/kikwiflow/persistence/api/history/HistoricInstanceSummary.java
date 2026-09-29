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
package io.kikwiflow.persistence.api.history;

import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;

/**
 * Uma instância como o histórico a conhece: montada a partir de {@code PROCESS_INSTANCE_STARTED} e, se houver,
 * {@code PROCESS_INSTANCE_FINISHED}. Continua existindo depois que a instância sai de {@code process_instances}.
 */
public record HistoricInstanceSummary(
        String processInstanceId,
        String processDefinitionId,
        String processDefinitionKey,
        Integer processDefinitionVersion,
        String businessKey,
        String tenantId,
        ProcessInstanceStatus status,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String startedBy,
        String origin
) {

    /** @param finished {@code null} enquanto a instância não terminou. */
    public static HistoricInstanceSummary of(ProcessInstanceStarted started, ProcessInstanceFinished finished) {
        return new HistoricInstanceSummary(
                started.id(),
                started.processDefinitionId(),
                started.processDefinitionKey(),
                started.processDefinitionVersion(),
                started.businessKey(),
                started.tenantId(),
                finished != null && finished.getStatus() != null ? finished.getStatus() : ProcessInstanceStatus.ACTIVE,
                started.startedAt(),
                finished != null ? finished.getEndedAt() : null,
                finished != null ? finished.getDurationMs() : null,
                started.actorId(),
                started.origin()
        );
    }
}
