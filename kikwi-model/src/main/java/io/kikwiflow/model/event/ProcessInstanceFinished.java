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

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.math.BigDecimal;
import java.time.Duration;
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
    private String processDefinitionKey;
    private Integer processDefinitionVersion;
    private Map<String, ProcessVariable> variables;
    private Instant startedAt;
    private Instant endedAt;
    private BigDecimal businessValue;
    private String tenantId;
    private String origin;
    private String parentInstanceId;
    private String callerTaskId;
    private String callerBranchId;

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Override
    public String processInstanceId() { return id; }

    @Override
    public String processDefinitionId() { return processDefinitionId; }
    @Override
    public String tenantId() { return tenantId; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public ProcessInstanceStatus getStatus() { return status; }
    public void setStatus(ProcessInstanceStatus status) { this.status = status; }
    public String getProcessDefinitionId() { return processDefinitionId; }
    public void setProcessDefinitionId(String processDefinitionId) { this.processDefinitionId = processDefinitionId; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }
    public Integer getProcessDefinitionVersion() { return processDefinitionVersion; }
    public void setProcessDefinitionVersion(Integer processDefinitionVersion) { this.processDefinitionVersion = processDefinitionVersion; }
    public Map<String, ProcessVariable> getVariables() { return variables; }
    public void setVariables(Map<String, ProcessVariable> variables) { this.variables = variables; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public BigDecimal getBusinessValue() { return businessValue; }
    public void setBusinessValue(BigDecimal businessValue) { this.businessValue = businessValue; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getParentInstanceId() { return parentInstanceId; }
    public void setParentInstanceId(String parentInstanceId) { this.parentInstanceId = parentInstanceId; }
    public String getCallerTaskId() { return callerTaskId; }
    public void setCallerTaskId(String callerTaskId) { this.callerTaskId = callerTaskId; }
    public String getCallerBranchId() { return callerBranchId; }
    public void setCallerBranchId(String callerBranchId) { this.callerBranchId = callerBranchId; }

    /**
     * Duração da instância em milissegundos, derivada de {@code startedAt}/{@code endedAt}.
     * Retorna {@code null} quando qualquer um dos dois timestamps está ausente.
     */
    public Long getDurationMs() {
        if (startedAt == null || endedAt == null) return null;
        return Duration.between(startedAt, endedAt).toMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String businessKey;
        private ProcessInstanceStatus status;
        private String processDefinitionId;
        private String processDefinitionKey;
        private Integer processDefinitionVersion;
        private Map<String, ProcessVariable> variables;
        private Instant startedAt;
        private Instant endedAt;
        private BigDecimal businessValue;
        private String tenantId;
        private String origin;
        private String parentInstanceId;
        private String callerTaskId;
        private String callerBranchId;

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

        public Builder processDefinitionKey(String processDefinitionKey) {
            this.processDefinitionKey = processDefinitionKey;
            return this;
        }

        public Builder processDefinitionVersion(Integer processDefinitionVersion) {
            this.processDefinitionVersion = processDefinitionVersion;
            return this;
        }

        public Builder variables(Map<String, ProcessVariable> variables) {
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

        public Builder businessValue(BigDecimal businessValue) {
            this.businessValue = businessValue;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder parentInstanceId(String parentInstanceId) {
            this.parentInstanceId = parentInstanceId;
            return this;
        }

        public Builder callerTaskId(String callerTaskId) {
            this.callerTaskId = callerTaskId;
            return this;
        }

        public Builder callerBranchId(String callerBranchId) {
            this.callerBranchId = callerBranchId;
            return this;
        }

        public ProcessInstanceFinished build() {
            ProcessInstanceFinished event = new ProcessInstanceFinished();
            event.setId(this.id);
            event.setBusinessKey(this.businessKey);
            event.setStatus(this.status);
            event.setProcessDefinitionId(this.processDefinitionId);
            event.setProcessDefinitionKey(this.processDefinitionKey);
            event.setProcessDefinitionVersion(this.processDefinitionVersion);
            event.setVariables(this.variables);
            event.setStartedAt(this.startedAt);
            event.setEndedAt(this.endedAt);
            event.setBusinessValue(this.businessValue);
            event.setTenantId(this.tenantId);
            event.setOrigin(this.origin);
            event.setParentInstanceId(this.parentInstanceId);
            event.setCallerTaskId(this.callerTaskId);
            event.setCallerBranchId(this.callerBranchId);
            return event;
        }
    }

}
