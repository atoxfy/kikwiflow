package io.kikwiflow.assertion;

import io.kikwiflow.event.LightweightEvent;
import io.kikwiflow.persistence.api.data.event.CriticalEvent;
import io.kikwiflow.persistence.api.data.event.FlowNodeExecuted;
import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;
import io.kikwiflow.persistence.api.data.event.ProcessInstanceFinished;
import io.kikwiflow.history.repository.FlowNodeExecutionSnapshotInMemoryRepository;
import io.kikwiflow.history.repository.ProcessInstanceInMemorySnapshotRepository;

import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssertableEventListener {

    private final Queue<OutboxEventEntity> outboxEventQueue;
    private final FlowNodeExecutionSnapshotInMemoryRepository flowNodeExecutionSnapshotRepository;
    private final ProcessInstanceInMemorySnapshotRepository processInstanceSnapshotRepository;

    public AssertableEventListener(Queue<OutboxEventEntity> outboxEventQueue, FlowNodeExecutionSnapshotInMemoryRepository flowNodeExecutionSnapshotRepository, ProcessInstanceInMemorySnapshotRepository processInstanceSnapshotRepository){
        this.outboxEventQueue = outboxEventQueue;
        this.flowNodeExecutionSnapshotRepository = flowNodeExecutionSnapshotRepository;
        this.processInstanceSnapshotRepository = processInstanceSnapshotRepository;
    }

    private void runOnce(){
        for (int i = 0; i < outboxEventQueue.size(); i++) {
            OutboxEventEntity outboxEvent = outboxEventQueue.poll();
            if (outboxEvent == null) {
                break;
            }

            CriticalEvent event = outboxEvent.getEvent();
            if(event instanceof FlowNodeExecuted flowNodeExecuted){
                flowNodeExecutionSnapshotRepository.save(flowNodeExecuted);
            } else if (event instanceof ProcessInstanceFinished processInstanceFinished) {
                processInstanceSnapshotRepository.save(processInstanceFinished);
            }
        }
    }


}
