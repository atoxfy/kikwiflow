package io.kikwiflow.event.model;

import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ProcessInstanceFinishedEvent(
         String id,
         String businessKey,
         ProcessInstanceStatus status,
         String processDefinitionId,
         Map<String, Object>variables,
         Instant startedAt,
         Instant endedAt) implements ExecutionEvent {

    public ProcessInstanceFinishedEvent {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(processDefinitionId, "processDefinitionId cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(endedAt, "endedAt cannot be null");
        variables = (variables == null) ? Collections.emptyMap() : Collections.unmodifiableMap(variables);
    }

    @Override
    public Instant getTimestamp() {
        return endedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String businessKey;
        private ProcessInstanceStatus status;
        private String processDefinitionId;
        private Map<String, Object> variables;
        private Instant startedAt;
        private Instant endedAt;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder businessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        public Builder status(ProcessInstanceStatus status) {
            this.status = status;
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

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder endedAt(Instant endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public ProcessInstanceFinishedEvent build() {
            return new ProcessInstanceFinishedEvent(id, businessKey, status, processDefinitionId, variables, startedAt, endedAt);
        }
    }
}
