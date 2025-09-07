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

import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;

import java.time.Instant;

public record ExecutableTask (String id,
                               String taskDefinitionId,
                               String name,
                               String description,
                               String processDefinitionId,
                               Instant createdAt,
                               Long executions,
                               Long retries,
                               String processInstanceId,
                               String error,
                               ExecutableTaskStatus status,
                               String executorId,
                               Instant acquiredAt,
                              Instant dueDate){

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder(){
        return new Builder()
                .id(this.id)
                .dueDate(this.dueDate)
                .executions(this.executions)
                .taskDefinitionId(this.taskDefinitionId)
                .name(this.name)
                .description(this.description)
                .processDefinitionId(this.processDefinitionId)
                .createdAt(this.createdAt)
                .retries(this.retries)
                .processInstanceId(this.processInstanceId)
                .error(this.error)
                .status(this.status)
                .executorId(this.executorId)
                .acquiredAt(this.acquiredAt);

    }

    public static class Builder {
        private String id;
        private String taskDefinitionId;
        private String name;
        private String description;
        private String processDefinitionId;
        private Instant createdAt = Instant.now();
        private Long executions = 0L;
        private Long retries = 0L;
        private String processInstanceId;
        private String error;
        private ExecutableTaskStatus status = ExecutableTaskStatus.PENDING;
        private String executorId;
        private Instant acquiredAt;
        private Instant dueDate;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder taskDefinitionId(String taskDefinitionId) { this.taskDefinitionId = taskDefinitionId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder processDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder executions(Long executions) { this.executions = executions; return this; }
        public Builder retries(Long retries) { this.retries = retries; return this; }
        public Builder processInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder status(ExecutableTaskStatus status) { this.status = status; return this; }
        public Builder executorId(String executorId) { this.executorId = executorId; return this; }
        public Builder acquiredAt(Instant acquiredAt) { this.acquiredAt = acquiredAt; return this; }
        public Builder dueDate(Instant dueDate) { this.dueDate = dueDate; return this; }

        public ExecutableTask build() {
            return new ExecutableTask(
                this.id,
                this.taskDefinitionId,
                this.name,
                this.description,
                this.processDefinitionId,
                this.createdAt,
                this.executions,
                this.retries,
                this.processInstanceId,
                this.error,
                this.status,
                this.executorId,
                this.acquiredAt,
                this.dueDate
            );
        }
    }

}
