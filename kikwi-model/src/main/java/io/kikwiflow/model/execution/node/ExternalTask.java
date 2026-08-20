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

package io.kikwiflow.model.execution.node;

import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;

import java.time.Instant;
import java.util.List;

/**
 * Represents a task waiting for an external trigger (e.g., Human Task, Receive Task).
 * This is distinct from an ExecutableTaskEntity (job), which is handled by an internal worker.
 */
public record ExternalTask (
         String id,
         String name,
         String description,
         String taskDefinitionId,
         String processInstanceId,
         String processDefinitionId,
         ExternalTaskStatus status,
         Instant createdAt,
         String topicName,
         String assignee,
         String tenantId,
         List<AttachedEventReference> boundaryEvents,
         AttachedTaskType attachedToRefType,
         String attachedToRefId,
         String attachedToRefDefinitionId,
         String joinTaskId,
         List<String> pendingBranchIds,
         String branchId,
         ExternalTaskType type,
         String correlationKey,
         String displayName,
         List<String> pendingCorrelationKeys,
         MatchPolicy matchPolicy,
         String coordinatorTaskId,
         Integer totalCorrelationKeys){

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .taskDefinitionId(this.taskDefinitionId)
                .processInstanceId(this.processInstanceId)
                .processDefinitionId(this.processDefinitionId)
                .status(this.status)
                .createdAt(this.createdAt)
                .topicName(this.topicName)
                .assignee(this.assignee)
                .tenantId(this.tenantId)
                .boundaryEvents(this.boundaryEvents)
                .attachedToRefType(this.attachedToRefType)
                .attachedToRefId(this.attachedToRefId)
                .attachedToRefDefinitionId(this.attachedToRefDefinitionId)
                .joinTaskId(this.joinTaskId)
                .pendingBranchIds(this.pendingBranchIds)
                .branchId(this.branchId)
                .type(this.type)
                .correlationKey(this.correlationKey)
                .displayName(this.displayName)
                .pendingCorrelationKeys(this.pendingCorrelationKeys)
                .matchPolicy(this.matchPolicy)
                .coordinatorTaskId(this.coordinatorTaskId)
                .totalCorrelationKeys(this.totalCorrelationKeys);
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String taskDefinitionId;
        private String processInstanceId;
        private String processDefinitionId;
        private ExternalTaskStatus status = ExternalTaskStatus.CREATED;
        private Instant createdAt = Instant.now();
        private String topicName;
        private String assignee;
        private String tenantId;
        private List<AttachedEventReference> boundaryEvents;
        private AttachedTaskType attachedToRefType;
        private String attachedToRefId;
        private String attachedToRefDefinitionId;
        private String joinTaskId;
        private List<String> pendingBranchIds;
        private String branchId;
        private ExternalTaskType type;
        private String correlationKey;
        private String displayName;
        private List<String> pendingCorrelationKeys;
        private MatchPolicy matchPolicy;
        private String coordinatorTaskId;
        private Integer totalCorrelationKeys;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder joinTaskId(String joinTaskId) { this.joinTaskId = joinTaskId; return this; }
        public Builder branchId(String branchId) { this.branchId = branchId; return this; }
        public Builder pendingBranchIds(List<String> pendingBranchIds) { this.pendingBranchIds = pendingBranchIds; return this; }
        public Builder attachedToRefType(AttachedTaskType attachedToRefType) { this.attachedToRefType = attachedToRefType; return this; }
        public Builder attachedToRefDefinitionId(String attachedToRefDefinitionId) { this.attachedToRefDefinitionId = attachedToRefDefinitionId; return this; }
        public Builder attachedToRefId(String attachedToRefId) { this.attachedToRefId = attachedToRefId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder taskDefinitionId(String taskDefinitionId) { this.taskDefinitionId = taskDefinitionId; return this; }
        public Builder processInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; return this; }
        public Builder processDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; return this; }
        public Builder status(ExternalTaskStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder topicName(String topicName) { this.topicName = topicName; return this; }
        public Builder assignee(String assignee) { this.assignee = assignee; return this; }
        public Builder boundaryEvents(List<AttachedEventReference> boundaryEvents) { this.boundaryEvents = boundaryEvents; return this; }
        public Builder type(ExternalTaskType type) { this.type = type; return this; }
        public Builder correlationKey(String correlationKey) { this.correlationKey = correlationKey; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder pendingCorrelationKeys(List<String> pendingCorrelationKeys) { this.pendingCorrelationKeys = pendingCorrelationKeys; return this; }
        public Builder matchPolicy(MatchPolicy matchPolicy) { this.matchPolicy = matchPolicy; return this; }
        public Builder coordinatorTaskId(String coordinatorTaskId) { this.coordinatorTaskId = coordinatorTaskId; return this; }
        public Builder totalCorrelationKeys(Integer totalCorrelationKeys) { this.totalCorrelationKeys = totalCorrelationKeys; return this; }

        public ExternalTask build() {
            return new ExternalTask(
                this.id,
                this.name,
                this.description,
                this.taskDefinitionId,
                this.processInstanceId,
                this.processDefinitionId,
                this.status,
                this.createdAt,
                this.topicName,
                this.assignee,
                this.tenantId,
                this.boundaryEvents,
                this.attachedToRefType,
                this.attachedToRefId,
                this.attachedToRefDefinitionId,
                this.joinTaskId,
                this.pendingBranchIds,
                this.branchId,
                this.type,
                this.correlationKey,
                this.displayName,
                this.pendingCorrelationKeys,
                this.matchPolicy,
                this.coordinatorTaskId,
                this.totalCorrelationKeys
            );
        }
    }
}