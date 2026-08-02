/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.persistence.mongodb.mapper.event;

import io.kikwiflow.model.event.TimerFired;
import io.kikwiflow.model.security.IdentityContext;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimerFiredMapperTest {

    @Test
    void roundTripsAllFields() {
        TimerFired original = new TimerFired(
                "proc-instance-1",
                "proc-def-1",
                "tenant-a",
                "REMINDER_NON_INTERRUPTIVE_TIMER",
                IdentityContext.system().actorId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS)
        );

        Document doc = TimerFiredMapper.toDocument(original);
        TimerFired restored = TimerFiredMapper.fromDocument(doc);

        assertEquals(original, restored);
    }

    @Test
    void roundTripsWithNullNextDueDate() {
        TimerFired original = new TimerFired(
                "proc-instance-2",
                "proc-def-1",
                "tenant-a",
                "REMINDER_NON_INTERRUPTIVE_TIMER",
                IdentityContext.system().actorId(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null
        );

        Document doc = TimerFiredMapper.toDocument(original);
        TimerFired restored = TimerFiredMapper.fromDocument(doc);

        assertEquals(original, restored);
    }
}
