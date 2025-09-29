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
package io.kikwiflow.model.definition.process;

import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ProcessDefinition(
        String id, Integer version, String key, String name, String description,
        Map<String, FlowNodeDefinition> flowNodes, FlowNodeDefinition defaultStartPoint, String checksum
) {
    public ProcessDefinition {
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
        private String checksum;
        private String description;
        private Map<String, FlowNodeDefinition> flowNodes = Collections.emptyMap();
        private FlowNodeDefinition defaultStartPoint;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder flowNodes(Map<String, FlowNodeDefinition> flowNodes) {
            this.flowNodes = flowNodes;
            return this;
        }

        public Builder defaultStartPoint(FlowNodeDefinition defaultStartPoint) {
            this.defaultStartPoint = defaultStartPoint;
            return this;
        }

        public ProcessDefinition build() {
            return new ProcessDefinition(id, version, key, name, description, flowNodes, defaultStartPoint, checksum);
        }
    }
}