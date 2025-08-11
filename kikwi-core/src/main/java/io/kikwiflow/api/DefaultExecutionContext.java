package io.kikwiflow.api;

import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.ProcessInstance;

public class DefaultExecutionContext implements ExecutionContext {
    private final ProcessInstance processInstance;
    private final ProcessDefinitionSnapshot processDefinition;
    private final FlowNodeDefinition flowNodeDefinition;

    public DefaultExecutionContext(ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition, FlowNodeDefinition flowNodeDefinition) {
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

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public ProcessDefinitionSnapshot getProcessDefinition() {
        return processDefinition;
    }

    @Override
    public FlowNodeDefinition getFlowNode() {
        return flowNodeDefinition;
    }
}
