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
