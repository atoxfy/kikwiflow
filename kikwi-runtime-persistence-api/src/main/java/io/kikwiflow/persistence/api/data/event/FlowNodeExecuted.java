package io.kikwiflow.persistence.api.data.event;


import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;

import java.time.Instant;

public class FlowNodeExecuted implements CriticalEvent {

    private String flowNodeDefinitionId;
    private String processDefinitionId;
    private String processInstanceId;
    private Instant startedAt;
    private Instant finishedAt;
    private NodeExecutionStatus nodeExecutionStatus;

    public String getFlowNodeDefinitionId() {
        return flowNodeDefinitionId;
    }

    public void setFlowNodeDefinitionId(String flowNodeDefinitionId) {
        this.flowNodeDefinitionId = flowNodeDefinitionId;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
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
        private String processInstanceId;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;

        private Builder() {}

        public Builder flowNodeDefinitionId(String flowNodeDefinitionId) {
            this.flowNodeDefinitionId = flowNodeDefinitionId;
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

        public Builder nodeExecutionStatus(NodeExecutionStatus nodeExecutionStatus) {
            this.nodeExecutionStatus = nodeExecutionStatus;
            return this;
        }

        public FlowNodeExecuted build() {
            FlowNodeExecuted event = new FlowNodeExecuted();
            event.setFlowNodeDefinitionId(this.flowNodeDefinitionId);
            event.setProcessDefinitionId(this.processDefinitionId);
            event.setProcessInstanceId(this.processInstanceId);
            event.setStartedAt(this.startedAt);
            event.setFinishedAt(this.finishedAt);
            event.setNodeExecutionStatus(this.nodeExecutionStatus);
            return event;
        }
    }
}