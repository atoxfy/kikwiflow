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
import java.util.UUID;

public class OutboxEventEntity {
    private String id;
    private Instant timestamp;
    private String event;
    private CriticalEvent payload;

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
}
