package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;

import java.time.Instant;

public record FlowNodeExecutionSnapshot(
        FlowNodeDefinition flowNodeDefinition,
        ProcessDefinition processDefinition,
        ProcessInstance processInstance,
        Instant startedAt,
        Instant finishedAt,
        NodeExecutionStatus nodeExecutionStatus) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FlowNodeDefinition flowNodeDefinition;
        private ProcessDefinition processDefinition;
        private ProcessInstance processInstance;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;

        private Builder() {}

        public Builder flowNodeDefinition(FlowNodeDefinition flowNodeDefinition) {
            this.flowNodeDefinition = flowNodeDefinition;
            return this;
        }

        public Builder processDefinitionSnapshot(ProcessDefinition processDefinition) {
            this.processDefinition = processDefinition;
            return this;
        }

        public Builder processInstanceSnapshot(ProcessInstance processInstance) {
            this.processInstance = processInstance;
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

        public FlowNodeExecutionSnapshot build() {
            return new FlowNodeExecutionSnapshot(flowNodeDefinition, processDefinition, processInstance, startedAt, finishedAt, nodeExecutionStatus);
        }
    }
}
