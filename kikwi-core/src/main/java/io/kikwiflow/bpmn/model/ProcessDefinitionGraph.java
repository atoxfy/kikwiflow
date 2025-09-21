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
package io.kikwiflow.bpmn.model;



import java.util.HashMap;
import java.util.Map;

public class ProcessDefinitionGraph {

    private String key;
    private String name;
    private String description;
    private Map<String, FlowNode> flowNodes = new HashMap<>();
    private FlowNode defaultStartPoint;
    private String checksum;

    public FlowNode getDefaultStartPoint() {
        return defaultStartPoint;
    }

    public void setDefaultStartPoint(FlowNode defaultStartPoint) {
        this.defaultStartPoint = defaultStartPoint;
    }

    public String getDescription() {
        return description;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void addFlowNode(FlowNode node) { this.flowNodes.put(node.getId(), node); }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, FlowNode> getFlowNodes() {
        return flowNodes;
    }

    public void setFlowNodes(Map<String, FlowNode> flowNodes) {
        this.flowNodes = flowNodes;
    }
}
