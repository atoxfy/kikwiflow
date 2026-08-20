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

import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.IncidentCreated;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.RetryScheduled;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxEventMapperTest {

    @Test
    void everyConstructedEventHasAGeneratedId() {
        FlowNodeFinished payload = FlowNodeFinished.builder()
                .processInstanceId("proc-instance-1")
                .nodeExecutionStatus(NodeExecutionStatus.SUCCESS)
                .build();

        OutboxEventEntity entity = new OutboxEventEntity(CriticalEventType.FLOW_NODE_FINISHED, payload);

        assertNotNull(entity.getId());
        assertTrue(!entity.getId().isBlank());
    }

    @Test
    void roundTripsFlowNodeFinishedEnvelope() {
        FlowNodeFinished payload = FlowNodeFinished.builder()
                .flowNodeDefinitionId("CALCULATE_CUSTOMER_RISK_ST")
                .processInstanceId("proc-instance-1")
                .processDefinitionId("proc-def-1")
                .nodeExecutionStatus(NodeExecutionStatus.SUCCESS)
                .build();

        OutboxEventEntity original = new OutboxEventEntity(CriticalEventType.FLOW_NODE_FINISHED, payload);

        Document doc = OutboxEventMapper.toDocument(original);
        assertEquals(original.getId(), doc.getString("_id"));
        assertEquals("FLOW_NODE_FINISHED", doc.getString("eventType"));
        assertEquals("proc-instance-1", doc.getString("processInstanceId"));
        assertEquals("proc-def-1", doc.getString("processDefinitionId"));
        assertEquals("PENDING", doc.getString("relayStatus"));

        OutboxEventEntity restored = OutboxEventMapper.fromDocument(doc);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getEvent(), restored.getEvent());
        assertTrue(restored.getPayload() instanceof FlowNodeFinished);
        assertEquals("CALCULATE_CUSTOMER_RISK_ST", ((FlowNodeFinished) restored.getPayload()).getFlowNodeDefinitionId());
    }

    @Test
    void roundTripsProcessInstanceFinishedEnvelope() {
        ProcessInstanceFinished payload = ProcessInstanceFinished.builder()
                .id("proc-instance-2")
                .status(ProcessInstanceStatus.COMPLETED)
                .processDefinitionId("proc-def-1")
                .build();

        OutboxEventEntity original = new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_FINISHED, payload);

        Document doc = OutboxEventMapper.toDocument(original);
        OutboxEventEntity restored = OutboxEventMapper.fromDocument(doc);

        assertTrue(restored.getPayload() instanceof ProcessInstanceFinished);
        assertEquals("proc-instance-2", ((ProcessInstanceFinished) restored.getPayload()).getId());
    }

    @Test
    void roundTripsIncidentCreatedEnvelope() {
        IncidentCreated payload = new IncidentCreated(
                "incident-1", "FAILED_JOB", "Connection timed out",
                "proc-def-1", "proc-instance-1", "tenant-a", "task-1", "CALCULATE_CUSTOMER_RISK_ST",
                IdentityContext.system().actorId(), Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );

        OutboxEventEntity original = new OutboxEventEntity(CriticalEventType.INCIDENT_CREATED, payload);

        Document doc = OutboxEventMapper.toDocument(original);
        assertEquals("INCIDENT_CREATED", doc.getString("eventType"));
        assertEquals("proc-instance-1", doc.getString("processInstanceId"));

        OutboxEventEntity restored = OutboxEventMapper.fromDocument(doc);
        assertTrue(restored.getPayload() instanceof IncidentCreated);
        assertEquals("incident-1", ((IncidentCreated) restored.getPayload()).incidentId());
    }

    @Test
    void roundTripsRetryScheduledEnvelope() {
        RetryScheduled payload = new RetryScheduled(
                "executable-task-1", "proc-def-1", "proc-instance-1", "tenant-a", "CALCULATE_CUSTOMER_RISK_ST",
                1L, 2L, Instant.now().plusSeconds(30).truncatedTo(ChronoUnit.MILLIS), "timeout",
                IdentityContext.system().actorId(), Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );

        OutboxEventEntity original = new OutboxEventEntity(CriticalEventType.RETRY_SCHEDULED, payload);

        Document doc = OutboxEventMapper.toDocument(original);
        OutboxEventEntity restored = OutboxEventMapper.fromDocument(doc);

        assertTrue(restored.getPayload() instanceof RetryScheduled);
        assertEquals("executable-task-1", ((RetryScheduled) restored.getPayload()).executableTaskId());
    }

    @Test
    void fromDocumentRejectsUnknownEventType() {
        Document doc = new Document("_id", "abc")
                .append("eventType", "SOME_UNKNOWN_EVENT")
                .append("payload", new Document());

        assertThrows(IllegalArgumentException.class, () -> OutboxEventMapper.fromDocument(doc));
    }
}
