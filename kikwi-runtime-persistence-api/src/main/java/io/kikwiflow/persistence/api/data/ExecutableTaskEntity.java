package io.kikwiflow.persistence.api.data;

import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;

import java.time.Instant;

public class ExecutableTaskEntity {
    private String id;
    private String taskDefinitionId;
    private String processDefinitionId;
    private Instant createdAt;
    private Long executions;
    private Long retries;
    private String processInstanceId;
    private String error;
    private ExecutableTaskStatus status;
    private String executorId;
    private Instant acquiredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskDefinitionId() {
        return taskDefinitionId;
    }

    public void setTaskDefinitionId(String taskDefinitionId) {
        this.taskDefinitionId = taskDefinitionId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getExecutions() {
        return executions;
    }

    public void setExecutions(Long executions) {
        this.executions = executions;
    }

    public Long getRetries() {
        return retries;
    }

    public void setRetries(Long retries) {
        this.retries = retries;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ExecutableTaskStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutableTaskStatus status) {
        this.status = status;
    }

    public String getExecutorId() {
        return executorId;
    }

    public void setExecutorId(String executorId) {
        this.executorId = executorId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(Instant acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String taskDefinitionId;
        private String processDefinitionId;
        private Instant createdAt;
        private Long executions;
        private Long retries;
        private String processInstanceId;
        private String error;
        private ExecutableTaskStatus status;
        private String executorId;
        private Instant acquiredAt;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder taskDefinitionId(String taskDefinitionId) {
            this.taskDefinitionId = taskDefinitionId;
            return this;
        }

        public Builder processDefinitionId(String processDefinitionId) {
            this.processDefinitionId = processDefinitionId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder executions(Long executions) {
            this.executions = executions;
            return this;
        }

        public Builder retries(Long retries) {
            this.retries = retries;
            return this;
        }

        public Builder processInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder status(ExecutableTaskStatus status) {
            this.status = status;
            return this;
        }

        public Builder executorId(String executorId) {
            this.executorId = executorId;
            return this;
        }

        public Builder acquiredAt(Instant acquiredAt) {
            this.acquiredAt = acquiredAt;
            return this;
        }

        public ExecutableTaskEntity build() {
            ExecutableTaskEntity entity = new ExecutableTaskEntity();
            entity.setId(this.id);
            entity.setTaskDefinitionId(this.taskDefinitionId);
            entity.setProcessDefinitionId(this.processDefinitionId);
            entity.setCreatedAt(this.createdAt);
            entity.setExecutions(this.executions);
            entity.setRetries(this.retries);
            entity.setProcessInstanceId(this.processInstanceId);
            entity.setError(this.error);
            entity.setStatus(this.status);
            entity.setExecutorId(this.executorId);
            entity.setAcquiredAt(this.acquiredAt);
            return entity;
        }
    }
}
