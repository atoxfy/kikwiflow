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

import io.kikwiflow.model.event.RetryScheduled;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class RetryScheduledMapper {

    private RetryScheduledMapper() {}

    public static Document toDocument(RetryScheduled event) {
        return new Document("executableTaskId", event.executableTaskId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("executionsSoFar", event.executionsSoFar())
                .append("retriesLeft", event.retriesLeft())
                .append("nextDueDate", event.nextDueDate() != null ? java.util.Date.from(event.nextDueDate()) : null)
                .append("errorMessage", event.errorMessage())
                .append("actorId", event.actorId())
                .append("scheduledAt", event.scheduledAt() != null ? java.util.Date.from(event.scheduledAt()) : null);
    }

    public static RetryScheduled fromDocument(Document doc) {
        return new RetryScheduled(
                doc.getString("executableTaskId"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("taskDefinitionId"),
                doc.getLong("executionsSoFar"),
                doc.getLong("retriesLeft"),
                InstantMapper.mapToInstant("nextDueDate", doc),
                doc.getString("errorMessage"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("scheduledAt", doc)
        );
    }
}
