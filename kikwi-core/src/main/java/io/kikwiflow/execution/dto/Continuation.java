package io.kikwiflow.execution.dto;

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

import java.util.List;

public record Continuation(List<FlowNodeDefinition> nextNodes, boolean isAsynchronous) {
}
