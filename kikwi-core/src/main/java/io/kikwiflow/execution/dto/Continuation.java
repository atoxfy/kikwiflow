package io.kikwiflow.execution.dto;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;

import java.util.List;

public record Continuation(List<FlowNodeDefinitionSnapshot> nextNodes, boolean isAsynchronous) {
}
