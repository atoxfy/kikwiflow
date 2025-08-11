package io.kikwiflow.persistence.api.model;

import io.kikwiflow.event.ExecutionEvent;

import java.time.Instant;

public record OutboxEvent(Instant timestamp, ExecutionEvent event) {
}
