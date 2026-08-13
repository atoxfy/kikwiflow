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

import io.kikwiflow.model.event.OrphanedChildCompletion;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class OrphanedChildCompletionMapper {

    private OrphanedChildCompletionMapper() {}

    public static Document toDocument(OrphanedChildCompletion event) {
        return new Document("processInstanceId", event.processInstanceId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("tenantId", event.tenantId())
                .append("parentInstanceId", event.parentInstanceId())
                .append("joinTaskId", event.joinTaskId())
                .append("branchId", event.branchId())
                .append("occurredAt", event.occurredAt() != null ? java.util.Date.from(event.occurredAt()) : null);
    }

    public static OrphanedChildCompletion fromDocument(Document doc) {
        return new OrphanedChildCompletion(
                doc.getString("processInstanceId"),
                doc.getString("processDefinitionId"),
                doc.getString("tenantId"),
                doc.getString("parentInstanceId"),
                doc.getString("joinTaskId"),
                doc.getString("branchId"),
                InstantMapper.mapToInstant("occurredAt", doc)
        );
    }
}
