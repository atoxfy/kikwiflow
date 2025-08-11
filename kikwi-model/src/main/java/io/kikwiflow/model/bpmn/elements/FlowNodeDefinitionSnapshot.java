package io.kikwiflow.model.bpmn.elements;

import java.util.List;

public record FlowNodeDefinitionSnapshot(
         String id,
         String name,
         String description,
         Boolean commitAfter,
         Boolean commitBefore,
         List<SequenceFlowDefinitionSnapshot> outgoing) {
}
