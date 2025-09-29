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

import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.bson.Document;

import java.util.Collections;

public final class ExecutableTaskMapper {

    private ExecutableTaskMapper() {}

    public static Document toDocument(ExecutableTask task) {
        if (task == null) return null;

        Document doc = new Document("_id", task.id())
                .append("taskDefinitionId", task.taskDefinitionId())
                .append("name", task.name())
                .append("description", task.description())
                .append("processDefinitionId", task.processDefinitionId())
                .append("createdAt", task.createdAt())
                .append("executions", task.executions())
                .append("retries", task.retries())
                .append("processInstanceId", task.processInstanceId())
                .append("error", task.error())
                .append("status", task.status() != null ? task.status().name() : null)
                .append("executorId", task.executorId())
                .append("acquiredAt", task.acquiredAt())
                .append("dueDate", task.dueDate())
                .append("attachedToRefId", task.attachedToRefId())
                .append("boundaryEvents", task.boundaryEvents());

        if (task.attachedToRefType() != null) {
            doc.append("attachedToRefType", task.attachedToRefType().name());
        }

        return doc;
    }

    public static ExecutableTask fromDocument(Document doc) {
        if (doc == null) return null;

        String statusStr = doc.getString("status");
        ExecutableTaskStatus status = statusStr != null ? ExecutableTaskStatus.valueOf(statusStr) : null;

        String attachedTypeStr = doc.getString("attachedToRefType");
        AttachedTaskType attachedType = attachedTypeStr != null ? AttachedTaskType.valueOf(attachedTypeStr) : null;

        return ExecutableTask.builder()
                .id(doc.getString("_id"))
                .taskDefinitionId(doc.getString("taskDefinitionId"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .processDefinitionId(doc.getString("processDefinitionId"))
                .createdAt(InstantMapper.mapToInstant("createdAt", doc))
                .executions(doc.getLong("executions"))
                .retries(doc.getLong("retries"))
                .processInstanceId(doc.getString("processInstanceId"))
                .error(doc.getString("error"))
                .status(status)
                .executorId(doc.getString("executorId"))
                .acquiredAt(InstantMapper.mapToInstant("acquiredAt", doc))
                .dueDate(InstantMapper.mapToInstant("dueDate", doc))
                .attachedToRefId(doc.getString("attachedToRefId"))
                .attachedToRefType(attachedType)
                .boundaryEvents(doc.getList("boundaryEvents", String.class, Collections.emptyList()))
                .build();
    }
}
