package io.kikwiflow.execution.dto;

import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;

public record FlowNodeExecutionResult(FlowNodeExecutionSnapshot flowNodeExecutionSnapshot, Continuation continuation) {
}
