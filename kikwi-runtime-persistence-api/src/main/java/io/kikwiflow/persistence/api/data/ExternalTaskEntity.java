package io.kikwiflow.persistence.api.data;

import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;

import java.time.Instant;

/**
 * Represents a task waiting for an external trigger (e.g., Human Task, Receive Task).
 * This is distinct from an ExecutableTaskEntity (job), which is handled by an internal worker.
 */
public class ExternalTaskEntity {
    private String id;
    private String taskDefinitionId;
    private String processInstanceId;
    private String processDefinitionId;
    private ExternalTaskStatus status;
    private Instant createdAt;
    private String topicName;
    private String assignee;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskDefinitionId() { return taskDefinitionId; }
    public void setTaskDefinitionId(String taskDefinitionId) { this.taskDefinitionId = taskDefinitionId; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; }
    public ExternalTaskStatus getStatus() { return status; }
    public void setStatus(ExternalTaskStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String taskDefinitionId;
        private String processInstanceId;
        private String processDefinitionId;
        private String topicName;
        private String assignee;

        public Builder taskDefinitionId(String val) { taskDefinitionId = val; return this; }
        public Builder processInstanceId(String val) { processInstanceId = val; return this; }
        public Builder processDefinitionId(String val) { processDefinitionId = val; return this; }
        public Builder topicName(String val) { topicName = val; return this; }
        public Builder assignee(String val) { assignee = val; return this; }

        public ExternalTaskEntity build() {
            ExternalTaskEntity entity = new ExternalTaskEntity();
            entity.setTaskDefinitionId(taskDefinitionId);
            entity.setProcessInstanceId(processInstanceId);
            entity.setProcessDefinitionId(processDefinitionId);
            entity.setTopicName(topicName);
            entity.setAssignee(assignee);
            entity.setStatus(ExternalTaskStatus.CREATED);
            entity.setCreatedAt(Instant.now());
            return entity;
        }
    }
}