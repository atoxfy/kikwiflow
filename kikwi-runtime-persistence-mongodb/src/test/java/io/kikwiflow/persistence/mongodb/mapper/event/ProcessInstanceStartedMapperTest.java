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

import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.execution.ProcessVariable;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessInstanceStartedMapperTest {

    @Test
    void roundTripsAllFields() {
        ProcessInstanceStarted original = new ProcessInstanceStarted(
                "proc-instance-1",
                "biz-key-1",
                "proc-def-1",
                "kyc-async",
                3,
                Map.of("customerId", new ProcessVariable("customerId", "12345")),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                new BigDecimal("199.90"),
                "tenant-a",
                "REST_API",
                "parent-instance-1",
                "caller-task-1",
                "caller-branch-1",
                "user-42"
        );

        Document doc = ProcessInstanceStartedMapper.toDocument(original);
        ProcessInstanceStarted restored = ProcessInstanceStartedMapper.fromDocument(doc);

        assertEquals(original.id(), restored.id());
        assertEquals(original.businessKey(), restored.businessKey());
        assertEquals(original.processDefinitionId(), restored.processDefinitionId());
        assertEquals(original.processDefinitionKey(), restored.processDefinitionKey());
        assertEquals(original.processDefinitionVersion(), restored.processDefinitionVersion());
        assertEquals(original.startedAt(), restored.startedAt());
        assertEquals(0, original.businessValue().compareTo(restored.businessValue()));
        assertEquals(original.tenantId(), restored.tenantId());
        assertEquals(original.origin(), restored.origin());
        assertEquals(original.parentInstanceId(), restored.parentInstanceId());
        assertEquals(original.callerTaskId(), restored.callerTaskId());
        assertEquals(original.callerBranchId(), restored.callerBranchId());
        assertEquals(original.actorId(), restored.actorId());
        assertEquals("12345", restored.variables().get("customerId").value());
    }

    @Test
    void roundTripsWithNullOptionalFields() {
        ProcessInstanceStarted original = new ProcessInstanceStarted(
                "proc-instance-2", "biz-key-2", "proc-def-1", "kyc-async", 1,
                null, Instant.now().truncatedTo(ChronoUnit.MILLIS), null, null, null, null, null, null, null
        );

        Document doc = ProcessInstanceStartedMapper.toDocument(original);
        ProcessInstanceStarted restored = ProcessInstanceStartedMapper.fromDocument(doc);

        assertEquals(original.id(), restored.id());
        assertEquals(original.startedAt(), restored.startedAt());
    }
}
