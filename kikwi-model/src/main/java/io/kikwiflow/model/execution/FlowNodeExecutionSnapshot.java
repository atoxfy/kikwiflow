package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;

import java.time.Instant;

public record FlowNodeExecutionSnapshot(
        FlowNodeDefinitionSnapshot flowNodeDefinition,
        ProcessDefinitionSnapshot processDefinitionSnapshot,
        ProcessInstanceSnapshot processInstanceSnapshot,
        Instant startedAt,
        Instant finishedAt,
        NodeExecutionStatus nodeExecutionStatus) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private FlowNodeDefinitionSnapshot flowNodeDefinition;
        private ProcessDefinitionSnapshot processDefinitionSnapshot;
        private ProcessInstanceSnapshot processInstanceSnapshot;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;

        private Builder() {}

        public Builder flowNodeDefinition(FlowNodeDefinitionSnapshot flowNodeDefinition) {
            this.flowNodeDefinition = flowNodeDefinition;
            return this;
        }

        public Builder processDefinitionSnapshot(ProcessDefinitionSnapshot processDefinitionSnapshot) {
            this.processDefinitionSnapshot = processDefinitionSnapshot;
            return this;
        }

        public Builder processInstanceSnapshot(ProcessInstanceSnapshot processInstanceSnapshot) {
            this.processInstanceSnapshot = processInstanceSnapshot;
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
            return new FlowNodeExecutionSnapshot(flowNodeDefinition, processDefinitionSnapshot, processInstanceSnapshot, startedAt, finishedAt, nodeExecutionStatus);
        }
    }
}
