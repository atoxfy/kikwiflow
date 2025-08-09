package io.kikwiflow.event;

import java.time.Instant;

public interface ExecutionEvent {
    Instant getTimestamp();
}