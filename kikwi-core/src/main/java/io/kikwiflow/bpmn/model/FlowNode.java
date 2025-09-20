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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlowNode {
    private String id;
    private String name;
    private String description;
    private Boolean commitAfter;
    private Boolean commitBefore;
    private List<SequenceFlow> outgoing = new ArrayList<>();
    private Map<String, String> extensionProperties;

    public String getId() {
        return id;
    }

    public void  addOutgoing(SequenceFlow sequenceFlow){
        this.outgoing.add(sequenceFlow);
    }

    public Map<String, String> getExtensionProperties() {
        return extensionProperties;
    }

    public void setExtensionProperties(Map<String, String> extensionProperties) {
        this.extensionProperties = extensionProperties;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<SequenceFlow> getOutgoing() {
        return outgoing;
    }

    public Boolean getCommitAfter() {
        return commitAfter;
    }

    public void setCommitAfter(Boolean commitAfter) {
        this.commitAfter = commitAfter;
    }

    public Boolean getCommitBefore() {
        return commitBefore;
    }

    public void setCommitBefore(Boolean commitBefore) {
        this.commitBefore = commitBefore;
    }
}
