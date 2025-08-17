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

package io.kikwiflow.model.bpmn.elements;

import io.kikwiflow.model.execution.node.Executable;

import java.util.Collections;
import java.util.List;

public record ServiceTaskDefinition(String id,
                                    String name,
                                    String description,
                                    String delegateExpression,
                                    Boolean commitAfter,
                                    Boolean commitBefore,
                                    List<SequenceFlowDefinition> outgoing) implements FlowNodeDefinition, Executable {

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

        private List<SequenceFlowDefinition> outgoing = Collections.emptyList();

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

        public Builder outgoing(List<SequenceFlowDefinition> outgoing) {
            if (outgoing != null) {
                this.outgoing = outgoing;
            }
            return this;
        }

        public ServiceTaskDefinition build() {
            return new ServiceTaskDefinition(id, name, description, delegateExpression, commitAfter, commitBefore, outgoing);
        }
    }

}
