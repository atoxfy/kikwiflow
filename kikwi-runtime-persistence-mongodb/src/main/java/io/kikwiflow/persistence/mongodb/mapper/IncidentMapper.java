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

package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import org.bson.Document;


public class IncidentMapper {
    public static Document toDocument(Incident incident) {
        return new Document("_id", incident.id())
                .append("type", incident.type())
                .append("message", incident.message())
                .append("processDefinitionId", incident.processDefinitionId())
                .append("processInstanceId", incident.processInstanceId())
                .append("stackTrace", incident.stackTrace())
                .append("executionId", incident.executionId())
                .append("createdAt", incident.createdAt() != null ? java.util.Date.from(incident.createdAt()) : null)
                .append("taskDefinitionId", incident.taskDefinitionId())
                .append("status", incident.status().name());
    }

    public static Incident fromDocument(Document doc) {
        return new Incident(
                doc.getString("_id"),
                doc.getString("type"),
                doc.getString("message"),
                doc.getString("stackTrace"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("executionId"),
                InstantMapper.mapToInstant("createdAt", doc),
                IncidentStatus.valueOf(doc.getString("status")),
               doc.getString("taskDefinitionId"));
    }
}
