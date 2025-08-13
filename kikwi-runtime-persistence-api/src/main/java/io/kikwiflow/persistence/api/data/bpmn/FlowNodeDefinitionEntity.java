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
package io.kikwiflow.persistence.api.data.bpmn;

import java.util.ArrayList;
import java.util.List;

public class FlowNodeDefinitionEntity {
    private String id;
    private String name;
    private String description;
    private Boolean commitAfter;
    private Boolean commitBefore;
    private List<SequenceFlowEntity> outgoing = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setOutgoing(List<SequenceFlowEntity> outgoing) {
        this.outgoing = outgoing;
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

    public List<SequenceFlowEntity> getOutgoing() {
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
