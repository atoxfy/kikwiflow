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

import io.kikwiflow.model.event.ProcessVariableChanged;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessVariableChangedMapperTest {

    @Test
    void roundTripsAllFields() {
        ProcessVariableChanged original = new ProcessVariableChanged(
                "proc-instance-1",
                "proc-def-1",
                "tenant-a",
                "riskScore",
                false,
                42,
                "user-42",
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );

        Document doc = ProcessVariableChangedMapper.toDocument(original);
        ProcessVariableChanged restored = ProcessVariableChangedMapper.fromDocument(doc);

        assertEquals(original.processInstanceId(), restored.processInstanceId());
        assertEquals(original.processDefinitionId(), restored.processDefinitionId());
        assertEquals(original.name(), restored.name());
        assertEquals(original.isTransient(), restored.isTransient());
        assertEquals(original.value(), restored.value());
        assertEquals(original.actorId(), restored.actorId());
        assertEquals(original.changedAt(), restored.changedAt());
    }

    @Test
    void roundTripsTransientStringValue() {
        ProcessVariableChanged original = new ProcessVariableChanged(
                "proc-instance-2",
                "proc-def-1",
                "tenant-a",
                "sessionToken",
                true,
                "abc-123",
                "user-99",
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );

        Document doc = ProcessVariableChangedMapper.toDocument(original);
        ProcessVariableChanged restored = ProcessVariableChangedMapper.fromDocument(doc);

        assertEquals(original, restored);
    }
}
