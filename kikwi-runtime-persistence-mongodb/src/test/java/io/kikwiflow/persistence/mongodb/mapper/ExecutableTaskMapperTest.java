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

package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.node.AttachedEventReference;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Antes deste teste, {@code ExecutableTaskMapper} não tinha nenhum round-trip verificado. Isso escondeu um bug
 * real: {@code toDocument} gravava {@code task.type()} como o enum Java cru em vez de {@code .name()} — o
 * driver nativo do Mongo não sabe codificar um enum arbitrário num {@code Document}, então qualquer
 * ExecutableTask com {@code type} não-nulo (ou seja, todo INTERRUPTIVE_TIMER/NON_INTERRUPTIVE_TIMER/
 * JOIN_GATEWAY/TIMER_TASK já criado pelo motor) teria falhado ao persistir contra um MongoDB real.
 */
class ExecutableTaskMapperTest {

    @Test
    void roundTripsStandardTask() {
        ExecutableTask original = ExecutableTask.builder()
                .id("task-1")
                .taskDefinitionId("PROCESS_DATA")
                .processInstanceId("proc-1")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.STANDARD)
                .status(ExecutableTaskStatus.PENDING)
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.STANDARD, restored.type());
        assertEquals(ExecutableTaskStatus.PENDING, restored.status());
        assertEquals(original.createdAt(), restored.createdAt());
    }

    @Test
    void roundTripsTimerTaskWithDueDate() {
        Instant dueDate = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS);

        ExecutableTask original = ExecutableTask.builder()
                .id("task-2")
                .taskDefinitionId("WAIT_SLA")
                .processInstanceId("proc-2")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.TIMER_TASK)
                .dueDate(dueDate)
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.TIMER_TASK, restored.type());
        assertEquals(dueDate, restored.dueDate());
        assertNull(restored.attachedToRefId());
    }

    @Test
    void roundTripsInterruptiveTimerAttachedToExternalTask() {
        ExecutableTask original = ExecutableTask.builder()
                .id("timer-1")
                .taskDefinitionId("TIMER_SLA_TIMEOUT")
                .processInstanceId("proc-3")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.INTERRUPTIVE_TIMER)
                .attachedToRefId("parent-1")
                .attachedToRefType(AttachedTaskType.EXTERNAL_TASK)
                .attachedToRefDefinitionId("WAIT_ALL_PRODUCTS")
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.INTERRUPTIVE_TIMER, restored.type());
        assertEquals("parent-1", restored.attachedToRefId());
        assertEquals(AttachedTaskType.EXTERNAL_TASK, restored.attachedToRefType());
        assertEquals("WAIT_ALL_PRODUCTS", restored.attachedToRefDefinitionId());
    }

    @Test
    void roundTripsBoundaryEventsWithMixedInstanceType() {
        ExecutableTask original = ExecutableTask.builder()
                .id("task-4")
                .taskDefinitionId("PROCESSAR_DADOS")
                .processInstanceId("proc-4")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.STANDARD)
                .boundaryEvents(List.of(
                        new AttachedEventReference("timer-instance-1", "TIMER_SLA_TIMEOUT", AttachedTaskType.EXECUTABLE_TASK),
                        new AttachedEventReference("catch-instance-1", "CANCEL_CATCH", AttachedTaskType.EXTERNAL_TASK)
                ))
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(2, restored.boundaryEvents().size());
        assertEquals(AttachedTaskType.EXECUTABLE_TASK, restored.boundaryEvents().get(0).instanceType());
        assertEquals(AttachedTaskType.EXTERNAL_TASK, restored.boundaryEvents().get(1).instanceType());
    }

    @Test
    void legacyBoundaryEventDocumentWithoutInstanceTypeDefaultsToExecutableTask() {
        // Simula um documento persistido antes de instanceType existir — só timers.
        org.bson.Document legacyBoundaryDoc = new org.bson.Document("instanceId", "timer-legacy")
                .append("definitionId", "TIMER_SLA_TIMEOUT");

        ExecutableTask original = ExecutableTask.builder()
                .id("task-5")
                .taskDefinitionId("PROCESSAR_DADOS")
                .processInstanceId("proc-5")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.STANDARD)
                .build();

        org.bson.Document doc = ExecutableTaskMapper.toDocument(original);
        doc.append("boundaryEvents", List.of(legacyBoundaryDoc));

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(doc);

        assertEquals(1, restored.boundaryEvents().size());
        assertEquals(AttachedTaskType.EXECUTABLE_TASK, restored.boundaryEvents().get(0).instanceType());
    }

    @Test
    void roundTripsCallActivityStarterWithLoopIndexAndLoopElement() {
        ExecutableTask original = ExecutableTask.builder()
                .id("starter-1")
                .taskDefinitionId("CALL_KYC_CHECK")
                .processInstanceId("proc-6")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.CALL_ACTIVITY_STARTER)
                .status(ExecutableTaskStatus.PENDING)
                .joinTaskId("coordinator-1")
                .branchId("coordinator-1:2")
                .loopIndex(2)
                .loopElement(new ProcessVariable("document", "doc-42.pdf"))
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.CALL_ACTIVITY_STARTER, restored.type());
        assertEquals("coordinator-1", restored.joinTaskId());
        assertEquals("coordinator-1:2", restored.branchId());
        assertEquals(2, restored.loopIndex());
        assertEquals("document", restored.loopElement().name());
        assertEquals("doc-42.pdf", restored.loopElement().value());
    }

    @Test
    void roundTripsNonInterruptiveTimerWithOccurrence() {
        ExecutableTask original = ExecutableTask.builder()
                .id("timer-cycle-2")
                .taskDefinitionId("RECURRING_PING")
                .processInstanceId("proc-8")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.NON_INTERRUPTIVE_TIMER)
                .attachedToRefId("parent-1")
                .attachedToRefType(AttachedTaskType.EXTERNAL_TASK)
                .occurrence(2)
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.NON_INTERRUPTIVE_TIMER, restored.type());
        assertEquals(2, restored.occurrence());
    }

    @Test
    void roundTripsCallActivityCoordinatorWithoutLoopElement() {
        ExecutableTask original = ExecutableTask.builder()
                .id("coordinator-2")
                .taskDefinitionId("CALL_KYC_CHECK")
                .processInstanceId("proc-7")
                .processDefinitionId("def-1")
                .type(ExecutableTaskType.CALL_ACTIVITY_COORDINATOR)
                .status(ExecutableTaskStatus.AWAITING_BRANCHES)
                .pendingBranchIds(List.of("coordinator-2:0"))
                .build();

        ExecutableTask restored = ExecutableTaskMapper.fromDocument(ExecutableTaskMapper.toDocument(original));

        assertEquals(ExecutableTaskType.CALL_ACTIVITY_COORDINATOR, restored.type());
        assertEquals(List.of("coordinator-2:0"), restored.pendingBranchIds());
        assertNull(restored.loopIndex());
        assertNull(restored.loopElement());
    }
}
