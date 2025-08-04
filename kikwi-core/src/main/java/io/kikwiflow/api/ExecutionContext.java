package io.kikwiflow.api;

import io.kikwiflow.model.bpmn.elements.FlowNode;

public interface ExecutionContext {
    void setVariable(String variableName, Object value);
    void removeVariable(String variableName);
    Object getVariable(String variableName);

    String getProcessInstanceId();

    FlowNode getFlowNode();


}
