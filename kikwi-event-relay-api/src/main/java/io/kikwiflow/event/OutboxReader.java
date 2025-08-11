package io.kikwiflow.event;

import io.kikwiflow.persistence.api.model.OutboxEvent;

import java.util.List;

public interface OutboxReader {
    List<OutboxEvent> readAndLockNextBatch(int batchSize);
    void confirmBatch(List<OutboxEvent> events);
}
