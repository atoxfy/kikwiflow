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

import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowNodeFinishedMapperTest {

    @Test
    void roundTripsAllFields() {
        Instant startedAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        Instant finishedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        FlowNodeFinished original = FlowNodeFinished.builder()
                .flowNodeDefinitionId("CALCULATE_CUSTOMER_RISK_ST")
                .flowNodeType("EXECUTABLE_TASK")
                .flowNodeName("Calcula risco do cliente")
                .flowNodeDescription("Avalia o risco de crédito com base no histórico")
                .processInstanceId("proc-instance-1")
                .processDefinitionId("proc-def-1")
                .processDefinitionKey("kyc-async")
                .interruptedByNodeDefinitionId("timer-1")
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .nodeExecutionStatus(NodeExecutionStatus.INTERRUPTED)
                .build();

        Document doc = FlowNodeFinishedMapper.toDocument(original);
        FlowNodeFinished restored = FlowNodeFinishedMapper.fromDocument(doc);

        assertEquals(original.getFlowNodeDefinitionId(), restored.getFlowNodeDefinitionId());
        assertEquals(original.getFlowNodeType(), restored.getFlowNodeType());
        assertEquals(original.getFlowNodeName(), restored.getFlowNodeName());
        assertEquals(original.getFlowNodeDescription(), restored.getFlowNodeDescription());
        assertEquals(original.getProcessInstanceId(), restored.getProcessInstanceId());
        assertEquals(original.getProcessDefinitionId(), restored.getProcessDefinitionId());
        assertEquals(original.getProcessDefinitionKey(), restored.getProcessDefinitionKey());
        assertEquals(original.getInterruptedByNodeDefinitionId(), restored.getInterruptedByNodeDefinitionId());
        assertEquals(startedAt, restored.getStartedAt());
        assertEquals(finishedAt, restored.getFinishedAt());
        assertEquals(NodeExecutionStatus.INTERRUPTED, restored.getNodeExecutionStatus());
    }

    @Test
    void roundTripsErrorDetailsWhenNodeExecutionFails() {
        FlowNodeFinished original = FlowNodeFinished.builder()
                .flowNodeDefinitionId("VALIDATE_DOCUMENT_ST")
                .processInstanceId("proc-instance-2")
                .processDefinitionId("proc-def-1")
                .nodeExecutionStatus(NodeExecutionStatus.ERROR)
                .errorType("java.lang.NullPointerException")
                .errorMessage("taxId não pode ser nulo")
                .errorStackTrace("java.lang.NullPointerException: taxId não pode ser nulo\n\tat ...")
                .build();

        Document doc = FlowNodeFinishedMapper.toDocument(original);
        FlowNodeFinished restored = FlowNodeFinishedMapper.fromDocument(doc);

        assertEquals(NodeExecutionStatus.ERROR, restored.getNodeExecutionStatus());
        assertEquals(original.getErrorType(), restored.getErrorType());
        assertEquals(original.getErrorMessage(), restored.getErrorMessage());
        assertEquals(original.getErrorStackTrace(), restored.getErrorStackTrace());
    }

    @Test
    void toDocumentDenormalizesProcessInstanceIdAtTopLevel() {
        FlowNodeFinished event = FlowNodeFinished.builder()
                .processInstanceId("proc-instance-42")
                .nodeExecutionStatus(NodeExecutionStatus.SUCCESS)
                .build();

        Document doc = FlowNodeFinishedMapper.toDocument(event);

        assertEquals("proc-instance-42", doc.getString("processInstanceId"));
    }
}
