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

import io.kikwiflow.model.event.TimerFired;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class TimerFiredMapper {

    private TimerFiredMapper() {}

    public static Document toDocument(TimerFired event) {
        return new Document("processInstanceId", event.processInstanceId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("tenantId", event.tenantId())
                .append("flowNodeDefinitionId", event.flowNodeDefinitionId())
                .append("actorId", event.actorId())
                .append("firedAt", event.firedAt() != null ? java.util.Date.from(event.firedAt()) : null)
                .append("nextDueDate", event.nextDueDate() != null ? java.util.Date.from(event.nextDueDate()) : null);
    }

    public static TimerFired fromDocument(Document doc) {
        return new TimerFired(
                doc.getString("processInstanceId"),
                doc.getString("processDefinitionId"),
                doc.getString("tenantId"),
                doc.getString("flowNodeDefinitionId"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("firedAt", doc),
                InstantMapper.mapToInstant("nextDueDate", doc)
        );
    }
}
