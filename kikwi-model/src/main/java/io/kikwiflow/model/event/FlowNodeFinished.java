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


import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;

import java.time.Instant;

public class FlowNodeFinished implements CriticalEvent {

    private String flowNodeDefinitionId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processInstanceId;
    private String tenantId;
    private String flowNodeType;
    private String flowNodeName;
    private String flowNodeDescription;
    private Instant startedAt;
    private String interruptedByNodeDefinitionId;
    private Instant finishedAt;
    private NodeExecutionStatus nodeExecutionStatus;
    private String errorType;
    private String errorMessage;
    private String errorStackTrace;

    public String getFlowNodeName() {
        return flowNodeName;
    }

    public void setFlowNodeName(String flowNodeName) {
        this.flowNodeName = flowNodeName;
    }

    public String getFlowNodeDescription() {
        return flowNodeDescription;
    }

    public void setFlowNodeDescription(String flowNodeDescription) {
        this.flowNodeDescription = flowNodeDescription;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(String errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    public String getFlowNodeDefinitionId() {
        return flowNodeDefinitionId;
    }

    public void setFlowNodeDefinitionId(String flowNodeDefinitionId) {
        this.flowNodeDefinitionId = flowNodeDefinitionId;
    }

    public String getFlowNodeType() {
        return flowNodeType;
    }

    public String getInterruptedByNodeDefinitionId() {
        return interruptedByNodeDefinitionId;
    }

    public void setInterruptedByNodeDefinitionId(String interruptedByNodeDefinitionId) {
        this.interruptedByNodeDefinitionId = interruptedByNodeDefinitionId;
    }

    public void setFlowNodeType(String flowNodeType) {
        this.flowNodeType = flowNodeType;
    }

    @Override
    public String processInstanceId() {
        return processInstanceId;
    }

    @Override
    public String processDefinitionId() {
        return processDefinitionId;
    }

    @Override
    public String tenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public void setProcessDefinitionKey(String processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public NodeExecutionStatus getNodeExecutionStatus() {
        return nodeExecutionStatus;
    }

    public void setNodeExecutionStatus(NodeExecutionStatus nodeExecutionStatus) {
        this.nodeExecutionStatus = nodeExecutionStatus;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String flowNodeDefinitionId;
        private String processDefinitionId;
        private String processDefinitionKey;
        private String processInstanceId;
        private String tenantId;
        private Instant startedAt;
        private Instant finishedAt;
        private String flowNodeType;
        private String flowNodeName;
        private String flowNodeDescription;
        private NodeExecutionStatus nodeExecutionStatus;
        private String interruptedByNodeDefinitionId;
        private String errorType;
        private String errorMessage;
        private String errorStackTrace;

        private Builder() {}

        public Builder flowNodeDefinitionId(String flowNodeDefinitionId) {
            this.flowNodeDefinitionId = flowNodeDefinitionId;
            return this;
        }

        public Builder interruptedByNodeDefinitionId(String interruptedByNodeDefinitionId) {
            this.interruptedByNodeDefinitionId = interruptedByNodeDefinitionId;
            return this;
        }

        public Builder flowNodeType(String flowNodeType) {
            this.flowNodeType = flowNodeType;
            return this;
        }

        public Builder flowNodeName(String flowNodeName) {
            this.flowNodeName = flowNodeName;
            return this;
        }

        public Builder flowNodeDescription(String flowNodeDescription) {
            this.flowNodeDescription = flowNodeDescription;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorStackTrace(String errorStackTrace) {
            this.errorStackTrace = errorStackTrace;
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

        public Builder processInstanceId(String processInstanceId) {
            this.processInstanceId = processInstanceId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
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

        public Builder nodeExecutionStatus(NodeExecutionStatus nodeExecutionStatus) {
            this.nodeExecutionStatus = nodeExecutionStatus;
            return this;
        }

        public FlowNodeFinished build() {
            FlowNodeFinished event = new FlowNodeFinished();
            event.setFlowNodeDefinitionId(this.flowNodeDefinitionId);
            event.setProcessDefinitionId(this.processDefinitionId);
            event.setProcessDefinitionKey(this.processDefinitionKey);
            event.setProcessInstanceId(this.processInstanceId);
            event.setTenantId(this.tenantId);
            event.setStartedAt(this.startedAt);
            event.setFinishedAt(this.finishedAt);
            event.setNodeExecutionStatus(this.nodeExecutionStatus);
            event.setFlowNodeType(this.flowNodeType);
            event.setFlowNodeName(this.flowNodeName);
            event.setFlowNodeDescription(this.flowNodeDescription);
            event.setInterruptedByNodeDefinitionId(this.interruptedByNodeDefinitionId);
            event.setErrorType(this.errorType);
            event.setErrorMessage(this.errorMessage);
            event.setErrorStackTrace(this.errorStackTrace);
            return event;
        }
    }
}