/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import org.bson.Document;

import java.time.Instant;
import java.util.Collections;


public final class ExternalTaskMapper {

    private ExternalTaskMapper() {}

    public static Document toDocument(ExternalTask task) {
        if (task == null) return null;

        return new Document("_id", task.id())
                .append("name", task.name())
                .append("description", task.description())
                .append("taskDefinitionId", task.taskDefinitionId())
                .append("processInstanceId", task.processInstanceId())
                .append("processDefinitionId", task.processDefinitionId())
                .append("status", task.status() != null ? task.status().name() : null)
                .append("createdAt", task.createdAt())
                .append("topicName", task.topicName())
                .append("assignee", task.assignee())
                .append("tenantId", task.tenantId())
                .append("boundaryEvents", task.boundaryEvents());
    }

    public static ExternalTask fromDocument(Document doc) {
        if (doc == null) return null;

        String statusStr = doc.getString("status");
        ExternalTaskStatus status = statusStr != null ? ExternalTaskStatus.valueOf(statusStr) : null;

        return ExternalTask.builder()
                .id(doc.getString("_id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .taskDefinitionId(doc.getString("taskDefinitionId"))
                .processInstanceId(doc.getString("processInstanceId"))
                .processDefinitionId(doc.getString("processDefinitionId"))
                .status(status)
                .createdAt(doc.get("createdAt", Instant.class))
                .topicName(doc.getString("topicName"))
                .assignee(doc.getString("assignee"))
                .tenantId(doc.getString("tenantId"))
                .boundaryEvents(doc.getList("boundaryEvents", String.class, Collections.emptyList()))
                .build();
    }
}
