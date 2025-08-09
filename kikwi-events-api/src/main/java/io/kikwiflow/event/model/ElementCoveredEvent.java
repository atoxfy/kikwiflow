package io.kikwiflow.event.model;


import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.event.model.enumerated.CoveredElementStatus;

import java.time.Instant;
import java.util.Objects;

public record ElementCoveredEvent (String elementId, String processDefinitionId, String processInstanceId, Instant startedAt, Instant finishedAt, CoveredElementStatus status) implements ExecutionEvent {

    public ElementCoveredEvent {
        Objects.requireNonNull(elementId, "elementId cannot be null");
        Objects.requireNonNull(processDefinitionId, "processDefinitionId cannot be null");
        Objects.requireNonNull(processInstanceId, "processInstanceId cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Instant getTimestamp() {
        return finishedAt;
    }

    public static class Builder {
        private String elementId;
        private String processDefinitionId;
        private String processInstanceId;
        private Instant startedAt;
        private Instant finishedAt;
        private CoveredElementStatus status;

        private Builder() {
        }

        public Builder elementId(String elementId) {
            this.elementId = elementId;
            return this;
        }

        public Builder processDefinitionId(String processDefinitionId) {
            this.processDefinitionId = processDefinitionId;
            return this;
        }

        public Builder processInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder finishedAt(Instant finishedAt) {
            this.finishedAt = finishedAt;
            return this;
        }

        public Builder status(CoveredElementStatus status) {
            this.status = status;
            return this;
        }

        public ElementCoveredEvent build() {
            return new ElementCoveredEvent(elementId, processDefinitionId, processInstanceId, startedAt, finishedAt, status);
        }
    }
}