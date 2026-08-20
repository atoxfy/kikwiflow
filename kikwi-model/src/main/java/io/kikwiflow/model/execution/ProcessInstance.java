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
package io.kikwiflow.model.execution;

import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


public record ProcessInstance(
        String id,
        String businessKey,
        BigDecimal businessValue,
        String tenantId,
        ProcessInstanceStatus status,
        String processDefinitionId,
        Map<String, ProcessVariable>
        variables, Instant startedAt,
        Instant endedAt,
        String origin,
        int version,
        String parentInstanceId,
        String callerTaskId,
        String callerBranchId,
        Map<String, Integer> activeNodes) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String businessKey;
        private ProcessInstanceStatus status;
        private String processDefinitionId;
        private Map<String, ProcessVariable> variables;
        private Instant startedAt;
        private Instant endedAt;
        private BigDecimal businessValue;
        private String tenantId;
        private String origin;
        private int version = 0;
        private String parentInstanceId;
        private String callerTaskId;
        private String callerBranchId;
        private Map<String, Integer> activeNodes = new java.util.HashMap<>();

        private Builder() {}

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder callerBranchId(String callerBranchId) {
            this.callerBranchId = callerBranchId;
            return this;
        }

        public Builder callerTaskId(String callerTaskId) {
            this.callerTaskId = callerTaskId;
            return this;
        }

        public Builder parentInstanceId(String parentInstanceId) {
            this.parentInstanceId = parentInstanceId;
            return this;
        }

        public Builder activeNodes(Map<String, Integer> activeNodes) {
            if (activeNodes != null) {
                this.activeNodes = activeNodes;
            }
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }


        public Builder businessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder status(ProcessInstanceStatus status) {
            this.status = status;
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

        public Builder processDefinitionId(String processDefinitionId) {
            this.processDefinitionId = processDefinitionId;
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

        public ProcessInstance build() {
            if(Objects.isNull(id)){
                id = UUID.randomUUID().toString();
                startedAt = Instant.now();
                status = ProcessInstanceStatus.ACTIVE;
            }

            return new ProcessInstance(id, businessKey, businessValue, tenantId, status, processDefinitionId, variables, startedAt, endedAt, origin, version, parentInstanceId, callerTaskId, callerBranchId, activeNodes);
        }
    }
}