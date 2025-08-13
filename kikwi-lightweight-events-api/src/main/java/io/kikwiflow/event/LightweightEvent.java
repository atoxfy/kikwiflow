package io.kikwiflow.event;

import java.time.Instant;

public interface LightweightEvent {
    Instant getTimestamp();
}