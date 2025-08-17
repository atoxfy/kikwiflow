/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kikwiflow.assertion;

import io.kikwiflow.model.event.CriticalEvent;
import io.kikwiflow.model.event.FlowNodeExecuted;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
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

    public void runOnce(){
        // A forma correta de esvaziar uma fila é usando um loop while com poll(),
        // que remove o elemento e o retorna, ou retorna null se a fila estiver vazia.
        OutboxEventEntity outboxEvent;
        while ((outboxEvent = outboxEventQueue.poll()) != null) {

            CriticalEvent event = outboxEvent.getEvent();
            if(event instanceof FlowNodeExecuted flowNodeExecuted){
                flowNodeExecutionSnapshotRepository.save(flowNodeExecuted);
            } else if (event instanceof ProcessInstanceFinished processInstanceFinished) {
                processInstanceSnapshotRepository.save(processInstanceFinished);
            }
        }
    }


}
