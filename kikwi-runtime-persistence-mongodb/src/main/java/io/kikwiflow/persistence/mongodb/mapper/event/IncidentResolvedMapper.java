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

import io.kikwiflow.model.event.IncidentResolved;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class IncidentResolvedMapper {

    private IncidentResolvedMapper() {}

    public static Document toDocument(IncidentResolved event) {
        return new Document("incidentId", event.incidentId())
                .append("type", event.type())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("executionId", event.executionId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("actorId", event.actorId())
                .append("resolvedAt", event.resolvedAt() != null ? java.util.Date.from(event.resolvedAt()) : null);
    }

    public static IncidentResolved fromDocument(Document doc) {
        return new IncidentResolved(
                doc.getString("incidentId"),
                doc.getString("type"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("executionId"),
                doc.getString("taskDefinitionId"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("resolvedAt", doc)
        );
    }
}
