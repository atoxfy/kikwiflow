package io.kikwiflow.model.bpmn.elements;

import java.util.List;

public sealed interface FlowNodeDefinitionSnapshot permits StartEventDefinitionSnapshot, ServiceTaskDefinitionSnapshot, EndEventDefinitionSnapshot {
    String id();
    String name();
    String description();
    Boolean commitAfter();
    Boolean commitBefore();
    List<SequenceFlowDefinitionSnapshot> outgoing();

}
