package io.kikwiflow.event;

import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;

import java.util.List;

public interface OutboxReader {
    List<OutboxEventEntity> readAndLockNextBatch(int batchSize);
    void confirmBatch(List<OutboxEventEntity> events);
}
