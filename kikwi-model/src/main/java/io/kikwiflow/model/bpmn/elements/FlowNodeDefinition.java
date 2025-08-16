package io.kikwiflow.model.bpmn.elements;

import java.util.List;

public sealed interface FlowNodeDefinition permits StartEventDefinition, HumanTaskDefinition, ServiceTaskDefinition, EndEventDefinition {
    String id();
    String name();
    String description();
    Boolean commitAfter();
    Boolean commitBefore();
    List<SequenceFlowDefinition> outgoing();
}
