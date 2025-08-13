package io.kikwiflow.persistence.api.data.event;

import java.time.Instant;

public class OutboxEventEntity {
    private Instant timestamp;
    private CriticalEvent event;

    public OutboxEventEntity(CriticalEvent event) {
        this.timestamp = Instant.now();
        this.event = event;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public CriticalEvent getEvent() {
        return event;
    }

    public void setEvent(CriticalEvent event) {
        this.event = event;
    }
}
