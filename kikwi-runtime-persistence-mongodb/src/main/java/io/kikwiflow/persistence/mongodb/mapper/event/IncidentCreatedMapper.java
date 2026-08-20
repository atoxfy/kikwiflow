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

import io.kikwiflow.model.event.IncidentCreated;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class IncidentCreatedMapper {

    private IncidentCreatedMapper() {}

    public static Document toDocument(IncidentCreated event) {
        return new Document("incidentId", event.incidentId())
                .append("type", event.type())
                .append("message", event.message())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("executionId", event.executionId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("actorId", event.actorId())
                .append("createdAt", event.createdAt() != null ? java.util.Date.from(event.createdAt()) : null);
    }

    public static IncidentCreated fromDocument(Document doc) {
        return new IncidentCreated(
                doc.getString("incidentId"),
                doc.getString("type"),
                doc.getString("message"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("executionId"),
                doc.getString("taskDefinitionId"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("createdAt", doc)
        );
    }
}
