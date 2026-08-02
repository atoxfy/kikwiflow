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

import io.kikwiflow.model.event.ExternalTaskClaimed;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class ExternalTaskClaimedMapper {

    private ExternalTaskClaimedMapper() {}

    public static Document toDocument(ExternalTaskClaimed event) {
        return new Document("externalTaskId", event.externalTaskId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("assignee", event.assignee())
                .append("actorId", event.actorId())
                .append("claimedAt", event.claimedAt() != null ? java.util.Date.from(event.claimedAt()) : null);
    }

    public static ExternalTaskClaimed fromDocument(Document doc) {
        return new ExternalTaskClaimed(
                doc.getString("externalTaskId"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("taskDefinitionId"),
                doc.getString("assignee"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("claimedAt", doc)
        );
    }
}
