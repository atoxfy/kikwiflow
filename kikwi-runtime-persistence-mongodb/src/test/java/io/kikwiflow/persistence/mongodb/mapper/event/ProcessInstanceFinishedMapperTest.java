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

import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcessInstanceFinishedMapperTest {

    @Test
    void roundTripsAllFieldsIncludingVariables() {
        Instant startedAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        Instant endedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ProcessInstanceFinished original = ProcessInstanceFinished.builder()
                .id("proc-instance-1")
                .businessKey("BK-001")
                .status(ProcessInstanceStatus.COMPLETED)
                .processDefinitionId("proc-def-1")
                .processDefinitionKey("kyc-async")
                .processDefinitionVersion(3)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .businessValue(new BigDecimal("1250.75"))
                .tenantId("tenant-acme")
                .origin("REST_API")
                .parentInstanceId("proc-instance-parent")
                .callerTaskId("call-activity-task-1")
                .callerBranchId("branch-1")
                .variables(Map.of(
                        "riskScore", new ProcessVariable("riskScore", 87.5),
                        "com.pontuacao", new ProcessVariable("com.pontuacao", "aprovado")
                ))
                .build();

        Document doc = ProcessInstanceFinishedMapper.toDocument(original);
        ProcessInstanceFinished restored = ProcessInstanceFinishedMapper.fromDocument(doc);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getBusinessKey(), restored.getBusinessKey());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getProcessDefinitionId(), restored.getProcessDefinitionId());
        assertEquals(original.getProcessDefinitionKey(), restored.getProcessDefinitionKey());
        assertEquals(original.getProcessDefinitionVersion(), restored.getProcessDefinitionVersion());
        assertEquals(startedAt, restored.getStartedAt());
        assertEquals(endedAt, restored.getEndedAt());
        assertEquals(0, original.getBusinessValue().compareTo(restored.getBusinessValue()));
        assertEquals(original.getTenantId(), restored.getTenantId());
        assertEquals(original.getOrigin(), restored.getOrigin());
        assertEquals(original.getParentInstanceId(), restored.getParentInstanceId());
        assertEquals(original.getCallerTaskId(), restored.getCallerTaskId());
        assertEquals(original.getCallerBranchId(), restored.getCallerBranchId());
        assertEquals(original.getDurationMs(), restored.getDurationMs());
        assertEquals(87.5, restored.getVariables().get("riskScore").value());
        assertEquals("aprovado", restored.getVariables().get("com.pontuacao").value());
    }

    @Test
    void processInstanceIdAccessorDelegatesToId() {
        ProcessInstanceFinished event = ProcessInstanceFinished.builder().id("proc-instance-99").build();

        assertEquals("proc-instance-99", event.processInstanceId());
    }

    @Test
    void toDocumentHandlesNullVariables() {
        ProcessInstanceFinished event = ProcessInstanceFinished.builder()
                .id("proc-instance-2")
                .status(ProcessInstanceStatus.COMPLETED)
                .build();

        Document doc = ProcessInstanceFinishedMapper.toDocument(event);

        assertNull(doc.get("variables"));
    }
}
