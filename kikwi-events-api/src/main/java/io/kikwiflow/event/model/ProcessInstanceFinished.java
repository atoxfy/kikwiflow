package io.kikwiflow.event.model;

import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;

import java.time.Instant;

public record ProcessInstanceFinished(Instant timestamp, ProcessInstanceSnapshot processInstanceSnapshot) implements ExecutionEvent {

    public ProcessInstanceFinished(ProcessInstanceSnapshot processInstanceSnapshot) {
        this(Instant.now(), processInstanceSnapshot);
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
