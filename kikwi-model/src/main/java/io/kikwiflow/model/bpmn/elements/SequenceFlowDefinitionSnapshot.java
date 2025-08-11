package io.kikwiflow.model.bpmn.elements;

public record SequenceFlowDefinitionSnapshot(
        String id,
        String condition,
        String targetNodeId) {
}
