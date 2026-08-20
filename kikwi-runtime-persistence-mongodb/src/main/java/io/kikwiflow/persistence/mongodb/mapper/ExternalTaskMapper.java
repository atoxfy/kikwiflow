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
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.node.AttachedEventReference;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExternalTask;
import org.bson.Document;

import java.util.Collections;
import java.util.List;


public final class ExternalTaskMapper {

    private ExternalTaskMapper() {}

    public static Document toDocument(ExternalTask task) {
        if (task == null) return null;

        Document doc = new Document("_id", task.id())
                .append("name", task.name())
                .append("description", task.description())
                .append("taskDefinitionId", task.taskDefinitionId())
                .append("processInstanceId", task.processInstanceId())
                .append("processDefinitionId", task.processDefinitionId())
                .append("status", task.status() != null ? task.status().name() : null)
                .append("createdAt", task.createdAt() != null ? java.util.Date.from(task.createdAt()) : null)
                .append("topicName", task.topicName())
                .append("assignee", task.assignee())
                .append("tenantId", task.tenantId())
                .append("joinTaskId", task.joinTaskId())
                .append("branchId", task.branchId())
                .append("pendingBranchIds", task.pendingBranchIds() != null ? task.pendingBranchIds() : null)
                .append("attachedToRefId", task.attachedToRefId())
                .append("attachedToRefDefinitionId", task.attachedToRefDefinitionId())
                .append("attachedToRefType", task.attachedToRefType() != null ? task.attachedToRefType().name() : null)
                .append("boundaryEvents", task.boundaryEvents() != null ?
                        task.boundaryEvents().stream()
                                .map(AttachedEventReferenceMapper::toDocument)
                                .toList() :
                        Collections.emptyList())
                .append("type", task.type() != null ? task.type().name() : null)
                .append("correlationKey", task.correlationKey())
                .append("displayName", task.displayName())
                .append("pendingCorrelationKeys", task.pendingCorrelationKeys())
                .append("matchPolicy", task.matchPolicy() != null ? task.matchPolicy().name() : null)
                .append("coordinatorTaskId", task.coordinatorTaskId())
                .append("totalCorrelationKeys", task.totalCorrelationKeys());

        if (task.attachedToRefDefinitionId() != null) {
            doc.append("attachedToRefDefinitionId", task.attachedToRefDefinitionId());
        }

        return doc;
    }

    public static ExternalTask fromDocument(Document doc) {
        if (doc == null) return null;

        String statusStr = doc.getString("status");
        ExternalTaskStatus status = statusStr != null ? ExternalTaskStatus.valueOf(statusStr) : null;

        List<Document> boundaryDocs = doc.getList("boundaryEvents", Document.class, Collections.emptyList());
        List<AttachedEventReference> boundaryEvents = boundaryDocs.stream()
                .map(AttachedEventReferenceMapper::fromDocument)
                .toList();

        List<String>  pendingBranches = doc.getList("pendingBranchIds", String.class, Collections.emptyList());

        String attachedTypeStr = doc.getString("attachedToRefType");
        AttachedTaskType attachedType = attachedTypeStr != null ? AttachedTaskType.valueOf(attachedTypeStr) : null;

        String typeStr = doc.getString("type");
        ExternalTaskType type = typeStr != null ? ExternalTaskType.valueOf(typeStr) : null;

        String matchPolicyStr = doc.getString("matchPolicy");
        MatchPolicy matchPolicy = matchPolicyStr != null ? MatchPolicy.valueOf(matchPolicyStr) : null;

        List<String> pendingCorrelationKeys = doc.get("pendingCorrelationKeys") != null
                ? doc.getList("pendingCorrelationKeys", String.class, Collections.emptyList())
                : null;

        return ExternalTask.builder()
                .id(doc.getString("_id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .taskDefinitionId(doc.getString("taskDefinitionId"))
                .processInstanceId(doc.getString("processInstanceId"))
                .processDefinitionId(doc.getString("processDefinitionId"))
                .status(status)
                .pendingBranchIds(pendingBranches)
                .branchId(doc.getString("branchId"))
                .joinTaskId(doc.getString("joinTaskId"))
                .createdAt(InstantMapper.mapToInstant("createdAt", doc))
                .topicName(doc.getString("topicName"))
                .tenantId(doc.getString("tenantId"))
                .assignee(doc.getString("assignee"))
                .attachedToRefId(doc.getString("attachedToRefId"))
                .attachedToRefDefinitionId(doc.getString("attachedToRefDefinitionId"))
                .attachedToRefType(attachedType)
                .boundaryEvents(boundaryEvents)
                .type(type)
                .correlationKey(doc.getString("correlationKey"))
                .displayName(doc.getString("displayName"))
                .pendingCorrelationKeys(pendingCorrelationKeys)
                .matchPolicy(matchPolicy)
                .coordinatorTaskId(doc.getString("coordinatorTaskId"))
                .totalCorrelationKeys(doc.getInteger("totalCorrelationKeys"))
                .build();
    }
}
