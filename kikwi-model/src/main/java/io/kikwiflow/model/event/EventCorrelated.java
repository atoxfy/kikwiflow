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

package io.kikwiflow.model.event;

import io.kikwiflow.model.execution.ProcessVariable;

import java.time.Instant;
import java.util.Map;

/**
 * Uma mensagem externa encontrou quem a esperava ({@code correlateMessage}/{@code correlateFromThrow}). Em modo
 * GROUP sai também quando a chave ainda não satisfaz a {@code matchPolicy} — é o único registro dessa entrega.
 */
public record EventCorrelated(
        String correlationKey,
        String externalTaskId,
        String processDefinitionId,
        String processInstanceId,
        String tenantId,
        String taskDefinitionId,
        String actorId,
        Instant correlatedAt,
        Map<String, ProcessVariable> submittedVariables
) implements CriticalEvent {

    public EventCorrelated {
        submittedVariables = submittedVariables != null ? submittedVariables : Map.of();
    }

    /** Cópia com outras variáveis — usada para mascarar na leitura sem mexer no evento gravado. */
    public EventCorrelated withSubmittedVariables(Map<String, ProcessVariable> newVariables) {
        return new EventCorrelated(correlationKey, externalTaskId, processDefinitionId, processInstanceId, tenantId,
                taskDefinitionId, actorId, correlatedAt, newVariables);
    }
}
