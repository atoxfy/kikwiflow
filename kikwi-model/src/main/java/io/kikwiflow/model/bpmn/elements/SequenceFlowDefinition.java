package io.kikwiflow.model.bpmn.elements;

public record SequenceFlowDefinition(
        String id,
        String condition,
        String targetNodeId) {
}
