package io.kikwiflow.model.bpmn.elements;

import io.kikwiflow.model.execution.ExecutableTask;

import java.util.Collections;
import java.util.List;

public record ServiceTaskDefinitionSnapshot(String id,
                                            String name,
                                            String description,
                                            String delegateExpression,
                                            Boolean commitAfter,
                                            Boolean commitBefore,
                                            List<SequenceFlowDefinitionSnapshot> outgoing) implements FlowNodeDefinitionSnapshot, ExecutableTask {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String delegateExpression;
        private Boolean commitAfter;
        private Boolean commitBefore;

        private List<SequenceFlowDefinitionSnapshot> outgoing = Collections.emptyList();

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder delegateExpression(String delegateExpression) {
            this.delegateExpression = delegateExpression;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder commitAfter(Boolean commitAfter) {
            this.commitAfter = commitAfter;
            return this;
        }

        public Builder commitBefore(Boolean commitBefore) {
            this.commitBefore = commitBefore;
            return this;
        }

        public Builder outgoing(List<SequenceFlowDefinitionSnapshot> outgoing) {
            if (outgoing != null) {
                this.outgoing = outgoing;
            }
            return this;
        }

        public ServiceTaskDefinitionSnapshot build() {
            return new ServiceTaskDefinitionSnapshot(id, name, description, delegateExpression, commitAfter, commitBefore, outgoing);
        }
    }

}
