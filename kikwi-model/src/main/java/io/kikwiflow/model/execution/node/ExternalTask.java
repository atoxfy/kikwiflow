package io.kikwiflow.model.execution.node;

import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;

import java.time.Instant;

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
         String assignee){

    public static Builder builder() {
        return new Builder();
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

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder taskDefinitionId(String taskDefinitionId) { this.taskDefinitionId = taskDefinitionId; return this; }
        public Builder processInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; return this; }
        public Builder processDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; return this; }
        public Builder status(ExternalTaskStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder topicName(String topicName) { this.topicName = topicName; return this; }
        public Builder assignee(String assignee) { this.assignee = assignee; return this; }

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
                this.assignee
            );
        }
    }
}