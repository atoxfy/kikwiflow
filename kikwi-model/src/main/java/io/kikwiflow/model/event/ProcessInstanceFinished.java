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

package io.kikwiflow.model.event;

import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;
import java.util.Map;

/**
 * A critical event representing the completion of a process instance.
 * This is a data transfer object (DTO), not a database entity. It captures the final state
 * of a process instance to be recorded in history or an outbox.
 */
public class ProcessInstanceFinished implements CriticalEvent {

    private String id;
    private String businessKey;
    private ProcessInstanceStatus status;
    private String processDefinitionId;
    private Map<String, Object> variables;
    private Instant startedAt;
    private Instant endedAt;

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public ProcessInstanceStatus getStatus() { return status; }
    public void setStatus(ProcessInstanceStatus status) { this.status = status; }
    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

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

        private Builder() {}

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

        public ProcessInstanceFinished build() {
            ProcessInstanceFinished event = new ProcessInstanceFinished();
            event.setId(this.id);
            event.setBusinessKey(this.businessKey);
            event.setStatus(this.status);
            event.setProcessDefinitionId(this.processDefinitionId);
            event.setVariables(this.variables);
            event.setStartedAt(this.startedAt);
            event.setEndedAt(this.endedAt);
            return event;
        }
    }

}
