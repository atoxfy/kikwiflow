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

package io.kikwiflow.persistence;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.exception.OptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKikwiEngineRepositoryTest {

    private InMemoryKikwiEngineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryKikwiEngineRepository(new ArrayDeque<OutboxEventEntity>());
    }

    @Test
    void savesMultipleVersionsOfTheSameProcessDefinitionKey() {
        ProcessDefinition v1 = ProcessDefinition.builder()
                .id("def-v1").key("test-process").version(1).checksum("checksum-v1").build();
        ProcessDefinition v2 = ProcessDefinition.builder()
                .id("def-v2").key("test-process").version(2).checksum("checksum-v2").build();

        repository.saveProcessDefinition(v1);
        repository.saveProcessDefinition(v2);

        assertEquals("def-v2", repository.findLatestVersionByKey("test-process").map(ProcessDefinition::id).orElseThrow());
        assertEquals("def-v1", repository.findByKeyAndChecksum("test-process", "checksum-v1").map(ProcessDefinition::id).orElseThrow());
        assertEquals(2, repository.findAProcessDefinitionsByParams("test-process").size());
    }

    @Test
    void commitWorkWithInstanceToCreatePersistsWithComputedActiveNodes() {
        ProcessInstance instance = ProcessInstance.builder()
                .id("proc-instance-1")
                .businessKey("BK-001")
                .processDefinitionId("def-v1")
                .status(ProcessInstanceStatus.ACTIVE)
                .startedAt(Instant.now())
                .variables(Map.of())
                .build();

        ExecutableTask task = ExecutableTask.builder()
                .id("task-1")
                .processInstanceId("proc-instance-1")
                .processDefinitionId("def-v1")
                .taskDefinitionId("NODE_A")
                .status(ExecutableTaskStatus.PENDING)
                .build();

        UnitOfWork uow = new UnitOfWork(instance, null, null,
                List.of(task), null, null, null, null, null, null, null, null, null, null, null);

        repository.commitWork(uow);

        ProcessInstance stored = repository.findProcessInstanceById("proc-instance-1").orElseThrow();
        assertEquals(1, stored.activeNodes().get("NODE_A"));
    }

    @Test
    void commitWorkWithInstanceToUpdateOnUnknownInstanceThrowsOptimisticLockingFailure() {
        ProcessInstance unknown = ProcessInstance.builder()
                .id("does-not-exist")
                .status(ProcessInstanceStatus.ACTIVE)
                .build();

        UnitOfWork uow = new UnitOfWork(null, unknown, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(OptimisticLockingFailureException.class, () -> repository.commitWork(uow));
    }

    @Test
    void branchPullIntentionResolvesJoinTaskToPendingWhenAllBranchesComplete() {
        ExecutableTask joinTask = ExecutableTask.builder()
                .id("join-1")
                .taskDefinitionId("JOIN_GATEWAY_A")
                .processInstanceId("proc-instance-1")
                .type(ExecutableTaskType.JOIN_GATEWAY)
                .status(ExecutableTaskStatus.AWAITING_BRANCHES)
                .pendingBranchIds(new java.util.ArrayList<>(List.of("branch-a", "branch-b")))
                .build();

        repository.commitWork(new UnitOfWork(null, null, null,
                List.of(joinTask), null, null, null, null, null, null, null, null, null, null, null));

        repository.commitWork(new UnitOfWork(null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(new BranchPullIntention("join-1", "branch-a")), null));

        ExecutableTask afterFirstPull = repository.findExecutableTaskById("join-1").orElseThrow();
        assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, afterFirstPull.status());
        assertEquals(List.of("branch-b"), afterFirstPull.pendingBranchIds());

        repository.commitWork(new UnitOfWork(null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of(new BranchPullIntention("join-1", "branch-b")), null));

        ExecutableTask afterSecondPull = repository.findExecutableTaskById("join-1").orElseThrow();
        assertEquals(ExecutableTaskStatus.PENDING, afterSecondPull.status());
        assertTrue(afterSecondPull.pendingBranchIds().isEmpty());
    }

    @Test
    void findAndLockDueTasksLocksPendingAndReclaimsStuckLockedTask() {
        ExecutableTask pending = ExecutableTask.builder()
                .id("task-pending")
                .taskDefinitionId("NODE_A")
                .processInstanceId("proc-instance-1")
                .status(ExecutableTaskStatus.PENDING)
                .dueDate(Instant.now().minusSeconds(5))
                .build();

        ExecutableTask stuckLocked = ExecutableTask.builder()
                .id("task-stuck")
                .taskDefinitionId("NODE_B")
                .processInstanceId("proc-instance-1")
                .status(ExecutableTaskStatus.LOCKED)
                .acquiredAt(Instant.now().minusSeconds(60))
                .build();

        repository.commitWork(new UnitOfWork(null, null, null,
                List.of(pending, stuckLocked), null, null, null, null, null, null, null, null, null, null, null));

        List<ExecutableTask> locked = repository.findAndLockDueTasks(Instant.now(), 10, "worker-1", 1000L);

        assertEquals(2, locked.size());
        assertTrue(locked.stream().allMatch(t -> t.status() == ExecutableTaskStatus.LOCKED && "worker-1".equals(t.executorId())));
    }

    @Test
    void processInstanceQueryFiltersAndPaginates() {
        seedInstance("pi-1", "tenant-a", ProcessInstanceStatus.ACTIVE);
        seedInstance("pi-2", "tenant-a", ProcessInstanceStatus.COMPLETED);
        seedInstance("pi-3", "tenant-b", ProcessInstanceStatus.ACTIVE);

        PageResult<ProcessInstanceSummary> result = repository.createProcessInstanceQuery()
                .tenantId("tenant-a")
                .statusIn(List.of(ProcessInstanceStatus.ACTIVE))
                .size(10)
                .page(0)
                .listSummary();

        assertEquals(1, result.totalElements());
        assertEquals("pi-1", result.content().get(0).id());
    }

    @Test
    void externalTaskQueryFiltersByProcessDefinitionAndAssignee() {
        ExternalTask t1 = ExternalTask.builder().id("ext-1").processDefinitionId("def-a").assignee("alice").build();
        ExternalTask t2 = ExternalTask.builder().id("ext-2").processDefinitionId("def-a").assignee("bob").build();
        ExternalTask t3 = ExternalTask.builder().id("ext-3").processDefinitionId("def-b").assignee("alice").build();

        repository.commitWork(new UnitOfWork(null, null, null,
                null, List.of(t1, t2, t3), null, null, null, null, null, null, null, null, null, null));

        List<ExternalTask> matches = repository.createExternalTaskQuery()
                .processDefinitionId("def-a")
                .assignee("alice")
                .list();

        assertEquals(1, matches.size());
        assertEquals("ext-1", matches.get(0).id());
    }

    private void seedInstance(String id, String tenantId, ProcessInstanceStatus status) {
        ProcessInstance instance = ProcessInstance.builder()
                .id(id)
                .tenantId(tenantId)
                .status(status)
                .processDefinitionId("def-a")
                .startedAt(Instant.now())
                .variables(Map.of())
                .build();
        repository.saveProcessInstance(instance);
    }
}
