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

package io.kikwiflow.api;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.execution.ProcessInstanceExecution;

public class DefaultExecutionContext implements ExecutionContext {

    private final ProcessInstanceExecution processInstance;
    private final ProcessDefinition processDefinition;
    private final FlowNodeDefinition flowNodeDefinition;

    public DefaultExecutionContext(ProcessInstanceExecution processInstance, ProcessDefinition processDefinition, FlowNodeDefinition flowNodeDefinition) {
        this.processInstance = processInstance;
        this.processDefinition = processDefinition;
        this.flowNodeDefinition = flowNodeDefinition;
    }

    @Override
    public void setVariable(String variableName, Object value) {
        processInstance.getVariables().put(variableName, value);
    }

    @Override
    public void removeVariable(String variableName) {
        processInstance.getVariables().remove(variableName);
    }

    @Override
    public Object getVariable(String variableName) {
        return processInstance.getVariables().get(variableName);
    }

    @Override
    public boolean hasVariable(String variableName) {
        return processInstance.getVariables().containsKey(variableName);
    }

    @Override
    public String getProcessInstanceId() {
        return processInstance.getId();
    }

    public ProcessInstanceExecution getProcessInstance() {
        return processInstance;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    @Override
    public FlowNodeDefinition getFlowNode() {
        return flowNodeDefinition;
    }
}
