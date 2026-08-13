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

import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.RetryStrategy;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.node.AttachedEventReference;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

public final class ExecutableTaskMapper {

    private ExecutableTaskMapper() {}

    public static Document toDocument(ExecutableTask task) {
        if (task == null) return null;

        Document doc = new Document("_id", task.id())
                .append("taskDefinitionId", task.taskDefinitionId())
                .append("name", task.name())
                .append("description", task.description())
                .append("type", task.type() != null ? task.type().name() : null)
                .append("processDefinitionId", task.processDefinitionId())
                .append("createdAt", task.createdAt() != null ? java.util.Date.from(task.createdAt()) : null)
                .append("executions", task.executions())
                .append("retries", task.retries())
                .append("processInstanceId", task.processInstanceId())
                .append("error", task.error())
                .append("status", task.status() != null ? task.status().name() : null)
                .append("executorId", task.executorId())
                .append("acquiredAt", task.acquiredAt())
                .append("dueDate", task.dueDate() != null ? java.util.Date.from(task.dueDate()) : null)
                .append("attachedToRefId", task.attachedToRefId())
                .append("joinTaskId", task.joinTaskId())
                .append("branchId", task.branchId())
                .append("pendingBranchIds", task.pendingBranchIds() != null ? task.pendingBranchIds() : null)
                .append("loopIndex", task.loopIndex())
                .append("occurrence", task.occurrence())
                .append("loopElement", ProcessVariableMapper.toDocument(task.loopElement()))
                .append("pendingLoopElements", task.pendingLoopElements() != null ?
                        task.pendingLoopElements().stream().map(ProcessVariableMapper::toDocument).toList() :
                        null)
                .append("attachedToRefType", task.attachedToRefType() != null ? task.attachedToRefType().name() : null)
                .append("boundaryEvents", task.boundaryEvents() != null ?
                        task.boundaryEvents().stream()
                                .map(AttachedEventReferenceMapper::toDocument)
                                .toList() :
                        Collections.emptyList());

        if (task.retryPolicy() != null) {
            doc.append("retryPolicy", new Document("strategy", task.retryPolicy().strategy().name())
                    .append("maxRetries", task.retryPolicy().maxRetries())
                    .append("initialInterval", task.retryPolicy().initialInterval())
                    .append("multiplier", task.retryPolicy().multiplier())
                    .append("maxInterval", task.retryPolicy().maxInterval())
                    .append("intervals", task.retryPolicy().intervals()));
        }

        if (task.attachedToRefDefinitionId() != null) {
            doc.append("attachedToRefDefinitionId", task.attachedToRefDefinitionId());
        }

        return doc;
    }

    public static ExecutableTask fromDocument(Document doc) {
        if (doc == null) return null;

        String statusStr = doc.getString("status");
        ExecutableTaskStatus status = statusStr != null ? ExecutableTaskStatus.valueOf(statusStr) : null;

        String attachedTypeStr = doc.getString("attachedToRefType");
        AttachedTaskType attachedType = attachedTypeStr != null ? AttachedTaskType.valueOf(attachedTypeStr) : null;
        List<Document> boundaryDocs = doc.getList("boundaryEvents", Document.class, Collections.emptyList());
        List<AttachedEventReference> boundaryEvents = boundaryDocs.stream()
                .map(AttachedEventReferenceMapper::fromDocument)
                .toList();

        List<String>  pendingBranches = doc.getList("pendingBranchIds", String.class, Collections.emptyList());

        List<Document> pendingLoopElementDocs = doc.getList("pendingLoopElements", Document.class, Collections.emptyList());
        List<ProcessVariable> pendingLoopElements = pendingLoopElementDocs.stream()
                .map(ProcessVariableMapper::fromDocumentToVariable)
                .toList();

        Document policyDoc = doc.get("retryPolicy", Document.class);
        RetryPolicy retryPolicy = null;
        if (policyDoc != null) {
            retryPolicy = new RetryPolicy(
                    RetryStrategy.valueOf(policyDoc.getString("strategy")),
                    policyDoc.getInteger("maxRetries"),
                    policyDoc.getString("initialInterval"),
                    policyDoc.getDouble("multiplier"),
                    policyDoc.getString("maxInterval"),
                    policyDoc.getList("intervals", String.class)
            );
        }


        return ExecutableTask.builder()
                .id(doc.getString("_id"))
                .retryPolicy(retryPolicy)
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
                .type(ExecutableTaskType.valueOf(doc.getString("type")))
                .pendingBranchIds(pendingBranches)
                .loopIndex(doc.getInteger("loopIndex"))
                .occurrence(doc.getInteger("occurrence"))
                .loopElement(ProcessVariableMapper.fromDocumentToVariable(doc.get("loopElement", Document.class)))
                .pendingLoopElements(pendingLoopElements)
                .branchId(doc.getString("branchId"))
                .joinTaskId(doc.getString("joinTaskId"))
                .executorId(doc.getString("executorId"))
                .acquiredAt(InstantMapper.mapToInstant("acquiredAt", doc))
                .dueDate(InstantMapper.mapToInstant("dueDate", doc))
                .attachedToRefId(doc.getString("attachedToRefId"))
                .attachedToRefType(attachedType)
                .attachedToRefDefinitionId(doc.getString("attachedToRefDefinitionId"))
                .boundaryEvents(boundaryEvents)
                .build();
    }
}
