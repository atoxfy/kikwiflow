package io.kikwiflow.event.model;


import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.model.execution.FlowNodeExecution;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;

import java.time.Instant;

public record FlowNodeExecuted(Instant timestamp, FlowNodeExecution flowNodeExecution) implements ExecutionEvent {

    public FlowNodeExecuted(FlowNodeExecution flowNodeExecution) {
        this(Instant.now(), flowNodeExecution);
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}