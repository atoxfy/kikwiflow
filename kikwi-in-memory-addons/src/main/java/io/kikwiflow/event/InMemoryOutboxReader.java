package io.kikwiflow.event;

import io.kikwiflow.persistence.api.model.OutboxEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class InMemoryOutboxReader implements OutboxReader {

    private final Queue<OutboxEvent> outboxQueue;

    public InMemoryOutboxReader(Queue<OutboxEvent> outboxQueue) {
        this.outboxQueue = outboxQueue;
    }

    @Override
    public List<OutboxEvent> readAndLockNextBatch(int batchSize) {

        List<OutboxEvent> batch = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            OutboxEvent event = outboxQueue.poll();
            if (event == null) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }

    @Override
    public void confirmBatch(List<OutboxEvent> events) {
        throw new RuntimeException("InMemoryOutboxReade don't implement confirmBatch");
    }
}
