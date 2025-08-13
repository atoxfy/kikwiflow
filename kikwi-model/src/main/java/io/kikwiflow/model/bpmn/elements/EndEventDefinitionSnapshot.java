package io.kikwiflow.model.bpmn.elements;

import java.util.Collections;
import java.util.List;

public record EndEventDefinitionSnapshot(String id,
                                         String name,
                                         String description,
                                         Boolean commitAfter,
                                         Boolean commitBefore,
                                         List<SequenceFlowDefinitionSnapshot> outgoing) implements FlowNodeDefinitionSnapshot {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
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

        public EndEventDefinitionSnapshot build() {
            return new EndEventDefinitionSnapshot(id, name, description, commitAfter, commitBefore, outgoing);
        }
    }
}
