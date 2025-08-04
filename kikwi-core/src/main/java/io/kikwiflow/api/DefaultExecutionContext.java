package io.kikwiflow.api;

import io.kikwiflow.api.ExecutionContext;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.model.execution.ProcessInstance;

public class DefaultExecutionContext implements ExecutionContext {
    private final ProcessInstance processInstance;
    private final ProcessDefinition processDefinition;
    private final FlowNode flowNode;

    public DefaultExecutionContext(ProcessInstance processInstance, ProcessDefinition processDefinition, FlowNode flowNode) {
        this.processInstance = processInstance;
        this.processDefinition = processDefinition;
        this.flowNode = flowNode;
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
    public String getProcessInstanceId() {
        return processInstance.getId();
    }

    public ProcessInstance getProcessInstance() {
        return processInstance;
    }

    public ProcessDefinition getProcessDefinition() {
        return processDefinition;
    }

    @Override
    public FlowNode getFlowNode() {
        return flowNode;
    }
}
