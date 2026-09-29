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

package io.kikwiflow.model.event;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class OutboxEventEntity {
    private String id;
    private Instant timestamp;
    private String event;
    private CriticalEvent payload;
    /**
     * Desempate de eventos com o mesmo {@link #timestamp} (precisão de milissegundo no Mongo). Vem de um contador
     * monotônico da JVM, então vale entre commits diferentes que caem no mesmo milissegundo — não só dentro de um
     * commit. Dois nós gravando a mesma instância no mesmo milissegundo ficam fora dessa garantia.
     */
    private long sequence;

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** Ordem de leitura do histórico: {@code timestamp}, e no empate, {@code sequence}. */
    public static final Comparator<OutboxEventEntity> HISTORY_ORDER =
            Comparator.comparing(OutboxEventEntity::getTimestamp).thenComparingLong(OutboxEventEntity::getSequence);

    /**
     * Carimba os eventos de um mesmo commit com um único {@code timestamp} e uma {@code sequence} crescente na
     * ordem da lista. Sem isso, cada evento carrega o {@code Instant.now()} de quando foi criado, e a ordem lida
     * do histórico passa a depender de em que momento cada {@code record*} rodou, não da ordem causal da lista.
     */
    public static void stampCommitOrder(List<OutboxEventEntity> events) {
        Instant committedAt = Instant.now();
        for (OutboxEventEntity event : events) {
            event.setTimestamp(committedAt);
            event.setSequence(SEQUENCE.incrementAndGet());
        }
    }

    public OutboxEventEntity(String event, CriticalEvent payload) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.event = event;
        this.payload = payload;
    }

    public OutboxEventEntity(CriticalEventType type, CriticalEvent payload) {
        this(type.name(), payload);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public CriticalEvent getPayload() {
        return payload;
    }

    public void setPayload(CriticalEvent payload) {
        this.payload = payload;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }
}
