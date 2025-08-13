/*
 * Copyright Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.model.bpmn;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ProcessDefinitionSnapshot(
        String id, Integer version, String key, String name,
        Map<String, FlowNodeDefinitionSnapshot> flowNodes, FlowNodeDefinitionSnapshot defaultStartPoint
) {
    public ProcessDefinitionSnapshot {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(flowNodes, "flowNodes cannot be null");
        flowNodes = Map.copyOf(flowNodes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Integer version;
        private String key;
        private String name;
        private Map<String, FlowNodeDefinitionSnapshot> flowNodes = Collections.emptyMap();
        private FlowNodeDefinitionSnapshot defaultStartPoint;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder flowNodes(Map<String, FlowNodeDefinitionSnapshot> flowNodes) {
            this.flowNodes = flowNodes;
            return this;
        }

        public Builder defaultStartPoint(FlowNodeDefinitionSnapshot defaultStartPoint) {
            this.defaultStartPoint = defaultStartPoint;
            return this;
        }

        public ProcessDefinitionSnapshot build() {
            return new ProcessDefinitionSnapshot(id, version, key, name, flowNodes, defaultStartPoint);
        }
    }
}