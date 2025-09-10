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
package io.kikwiflow.bpmn.model.task;


import io.kikwiflow.bpmn.model.FlowNode;
import io.kikwiflow.bpmn.model.boundary.BoundaryEvent;
import io.kikwiflow.model.execution.node.Executable;

import java.util.ArrayList;
import java.util.List;

public class ServiceTask extends FlowNode implements Executable {

    private String delegateExpression;

    private List<BoundaryEvent> boundaryEvents = new ArrayList<>();

    public void  addBoundaryEvent(BoundaryEvent boundaryEvent){
        this.boundaryEvents.add(boundaryEvent);
    }

    public List<BoundaryEvent> getBoundaryEvents() {
        return boundaryEvents;
    }

    public String getDelegateExpression() {
        return delegateExpression;
    }

    public void setDelegateExpression(String delegateExpression) {
        this.delegateExpression = delegateExpression;
    }
}
