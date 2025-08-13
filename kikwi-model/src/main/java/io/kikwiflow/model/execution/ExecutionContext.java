package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;

public interface ExecutionContext {
    void setVariable(String variableName, Object value);
    void removeVariable(String variableName);
    Object getVariable(String variableName);

    boolean hasVariable(String variableName);

    String getProcessInstanceId();

    FlowNodeDefinitionSnapshot getFlowNode();


}
