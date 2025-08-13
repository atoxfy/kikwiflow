package io.kikwiflow.persistence.api.data;

import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;
import java.util.Map;

public class ProcessInstanceEntity {
    private String id;
    private String businessKey;
    private ProcessInstanceStatus status;
    private String processDefinitionId;
    private Map<String, Object> variables;
    private Instant startedAt;
    private Instant endedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public ProcessInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessInstanceStatus status) {
        this.status = status;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String businessKey;
        private String processDefinitionId;
        private Map<String, Object> variables;

        private Builder() {
        }

        public Builder businessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        public Builder processDefinitionId(String processDefinitionId) {
            this.processDefinitionId = processDefinitionId;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public ProcessInstanceEntity build() {
            ProcessInstanceEntity instance = new ProcessInstanceEntity();
            instance.setBusinessKey(this.businessKey);
            instance.setProcessDefinitionId(this.processDefinitionId);
            instance.setVariables(this.variables);
            instance.setStatus(ProcessInstanceStatus.ACTIVE);
            instance.setStartedAt(Instant.now());
            return instance;
        }
    }
}
