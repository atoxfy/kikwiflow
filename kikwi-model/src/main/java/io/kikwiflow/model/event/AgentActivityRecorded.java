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

import java.time.Instant;
import java.util.Map;

/**
 * Payload único dos eventos {@code AGENT_*} (docs/engine/31 E3): o tipo concreto vem do envelope
 * ({@link OutboxEventEntity#getEvent()}). {@code data} carrega só hashes, ids e referências
 * ({@code conversationRef}, {@code dms://...}) — nunca conteúdo de mensagem (claim check). Valores aceitos:
 * {@code String}, {@code Number}, {@code Boolean}, listas e mapas desses.
 *
 * @param at quando a atividade aconteceu no runtime; o momento da gravação é o {@code timestamp} do envelope
 */
public record AgentActivityRecorded(
        String externalTaskId,
        String processDefinitionId,
        String processInstanceId,
        String tenantId,
        String taskDefinitionId,
        String workerId,
        Instant at,
        Map<String, Object> data
) implements CriticalEvent {

    public AgentActivityRecorded {
        data = data != null ? data : Map.of();
    }

    @Override
    public String actorId() {
        return workerId;
    }
}
