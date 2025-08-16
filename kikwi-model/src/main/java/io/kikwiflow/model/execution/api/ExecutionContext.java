package io.kikwiflow.model.execution.api;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

public interface ExecutionContext {
    void setVariable(String variableName, Object value);
    void removeVariable(String variableName);
    Object getVariable(String variableName);

    boolean hasVariable(String variableName);

    String getProcessInstanceId();

    FlowNodeDefinition getFlowNode();


}
