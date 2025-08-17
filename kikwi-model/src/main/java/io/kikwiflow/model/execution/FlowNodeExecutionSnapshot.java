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
