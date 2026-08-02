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

package io.kikwiflow.persistence.mongodb.mapper.event;

import io.kikwiflow.model.event.CriticalEvent;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskClaimed;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.ExternalTaskUnclaimed;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.IncidentCreated;
import io.kikwiflow.model.event.IncidentResolved;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.event.ProcessVariableChanged;
import io.kikwiflow.model.event.RetryScheduled;
import io.kikwiflow.model.event.TimerFired;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

import java.util.Map;
import java.util.function.Function;

/**
 * Mapeia o envelope {@link OutboxEventEntity} para/de {@link Document}, despachando a (de)serialização do
 * payload polimórfico ({@link CriticalEvent}) com base no catálogo {@link CriticalEventType} — mesma fonte de
 * verdade usada para construir os eventos em {@code kikwi-core}, evitando dois pontos de manutenção separados
 * para o mesmo conjunto de tipos.
 */
public final class OutboxEventMapper {

    public static final String COLLECTION_RELAY_STATUS_PENDING = "PENDING";

    private static final Map<String, Function<Document, CriticalEvent>> FROM_DOCUMENT_MAPPERS = Map.ofEntries(
            Map.entry(CriticalEventType.FLOW_NODE_FINISHED.name(), FlowNodeFinishedMapper::fromDocument),
            Map.entry(CriticalEventType.GATEWAY_ANSWER_RESOLVED.name(), GatewayAnswerResolvedMapper::fromDocument),
            Map.entry(CriticalEventType.PROCESS_INSTANCE_FINISHED.name(), ProcessInstanceFinishedMapper::fromDocument),
            Map.entry(CriticalEventType.PROCESS_INSTANCE_STARTED.name(), ProcessInstanceStartedMapper::fromDocument),
            Map.entry(CriticalEventType.INCIDENT_CREATED.name(), IncidentCreatedMapper::fromDocument),
            Map.entry(CriticalEventType.INCIDENT_RESOLVED.name(), IncidentResolvedMapper::fromDocument),
            Map.entry(CriticalEventType.EXTERNAL_TASK_CLAIMED.name(), ExternalTaskClaimedMapper::fromDocument),
            Map.entry(CriticalEventType.EXTERNAL_TASK_UNCLAIMED.name(), ExternalTaskUnclaimedMapper::fromDocument),
            Map.entry(CriticalEventType.EXTERNAL_TASK_COMPLETED.name(), ExternalTaskCompletedMapper::fromDocument),
            Map.entry(CriticalEventType.RETRY_SCHEDULED.name(), RetryScheduledMapper::fromDocument),
            Map.entry(CriticalEventType.PROCESS_VARIABLE_CHANGED.name(), ProcessVariableChangedMapper::fromDocument),
            Map.entry(CriticalEventType.TIMER_FIRED.name(), TimerFiredMapper::fromDocument)
    );

    private OutboxEventMapper() {}

    public static Document toDocument(OutboxEventEntity entity) {
        Document payloadDoc = switch (entity.getPayload()) {
            case FlowNodeFinished e -> FlowNodeFinishedMapper.toDocument(e);
            case GatewayAnswerResolved e -> GatewayAnswerResolvedMapper.toDocument(e);
            case ProcessInstanceFinished e -> ProcessInstanceFinishedMapper.toDocument(e);
            case ProcessInstanceStarted e -> ProcessInstanceStartedMapper.toDocument(e);
            case IncidentCreated e -> IncidentCreatedMapper.toDocument(e);
            case IncidentResolved e -> IncidentResolvedMapper.toDocument(e);
            case ExternalTaskClaimed e -> ExternalTaskClaimedMapper.toDocument(e);
            case ExternalTaskUnclaimed e -> ExternalTaskUnclaimedMapper.toDocument(e);
            case ExternalTaskCompleted e -> ExternalTaskCompletedMapper.toDocument(e);
            case RetryScheduled e -> RetryScheduledMapper.toDocument(e);
            case ProcessVariableChanged e -> ProcessVariableChangedMapper.toDocument(e);
            case TimerFired e -> TimerFiredMapper.toDocument(e);
            default -> throw new IllegalArgumentException(
                    "Kikwiflow Outbox: payload de evento crítico desconhecido: " + entity.getPayload().getClass());
        };

        return new Document("_id", entity.getId())
                .append("eventType", entity.getEvent())
                .append("processInstanceId", entity.getPayload().processInstanceId())
                .append("processDefinitionId", entity.getPayload().processDefinitionId())
                .append("timestamp", entity.getTimestamp() != null ? java.util.Date.from(entity.getTimestamp()) : null)
                .append("relayStatus", COLLECTION_RELAY_STATUS_PENDING)
                .append("lockedUntil", null)
                .append("payload", payloadDoc);
    }

    public static OutboxEventEntity fromDocument(Document doc) {
        String eventType = doc.getString("eventType");
        Function<Document, CriticalEvent> mapper = FROM_DOCUMENT_MAPPERS.get(eventType);
        if (mapper == null) {
            throw new IllegalArgumentException("Kikwiflow Outbox: tipo de evento desconhecido no documento: " + eventType);
        }

        CriticalEvent payload = mapper.apply(doc.get("payload", Document.class));
        OutboxEventEntity entity = new OutboxEventEntity(eventType, payload);
        entity.setId(doc.getString("_id"));
        entity.setTimestamp(InstantMapper.mapToInstant("timestamp", doc));
        return entity;
    }
}
