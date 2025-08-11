package io.kikwiflow.assertion;

import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.event.model.FlowNodeExecuted;
import io.kikwiflow.event.model.ProcessInstanceFinished;
import io.kikwiflow.history.repository.FlowNodeExecutionRepository;
import io.kikwiflow.history.repository.ProcessInstanceSnapshotRepository;
import io.kikwiflow.persistence.api.model.OutboxEvent;

import java.util.Queue;

public class AssertableEventListener {

    private final ProcessInstanceSnapshotRepository processInstanceSnapshotRepository;
    private final Queue<OutboxEvent> outboxEventQueue;
    private final FlowNodeExecutionRepository flowNodeExecutionRepository;

    public AssertableEventListener(Queue<OutboxEvent> outboxEventQueue, ProcessInstanceSnapshotRepository processInstanceSnapshotRepository, FlowNodeExecutionRepository flowNodeExecutionRepository){
         this.outboxEventQueue = outboxEventQueue;
         this.processInstanceSnapshotRepository = processInstanceSnapshotRepository;
        this.flowNodeExecutionRepository = flowNodeExecutionRepository;
    }

    public void runOnce(){
        for (int i = 0; i < outboxEventQueue.size(); i++) {
            OutboxEvent outboxEvent = outboxEventQueue.poll();
            if (outboxEvent == null) {
                break;
            }

            ExecutionEvent event = outboxEvent.event();
            if(event instanceof FlowNodeExecuted flowNodeExecuted){
                flowNodeExecutionRepository.save(flowNodeExecuted.flowNodeExecution());
            } else if (event instanceof ProcessInstanceFinished processInstanceFinished) {
                processInstanceSnapshotRepository.save(processInstanceFinished.processInstanceSnapshot());
            }
        }
    }
}
