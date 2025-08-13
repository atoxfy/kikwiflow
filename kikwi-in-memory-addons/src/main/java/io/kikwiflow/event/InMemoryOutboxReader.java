package io.kikwiflow.event;

import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class InMemoryOutboxReader implements OutboxReader {

    private final Queue<OutboxEventEntity> outboxQueue;

    public InMemoryOutboxReader(Queue<OutboxEventEntity> outboxQueue) {
        this.outboxQueue = outboxQueue;
    }

    @Override
    public List<OutboxEventEntity> readAndLockNextBatch(int batchSize) {

        List<OutboxEventEntity> batch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            OutboxEventEntity event = outboxQueue.poll();
            if (event == null) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }

    @Override
    public void confirmBatch(List<OutboxEventEntity> events) {
        throw new RuntimeException("InMemoryOutboxReade don't implement confirmBatch");
    }
}
