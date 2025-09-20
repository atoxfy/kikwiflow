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

package io.kikwiflow.event;

import io.kikwiflow.model.event.OutboxEventEntity;

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
