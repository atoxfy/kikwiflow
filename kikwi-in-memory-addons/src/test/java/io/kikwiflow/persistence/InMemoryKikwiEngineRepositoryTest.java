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
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.exception.OptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Cobre a cascata genérica de EVENT_CATCHER GROUP documentada em {@code commitWork}: apagar a tarefa-mãe
     * também apaga qualquer filha cujo {@code coordinatorTaskId} aponte para ela, sem nenhuma lógica
     * específica de EVENT_CATCHER na camada acima (ContinuationService).
     */
    @Nested
    class EventCatcherCascadeAndCorrelation {

        @Test
        void deletingParentCascadesToItsChildrenButNotToUnrelatedTasks() {
            ExternalTask parent = ExternalTask.builder()
                    .id("parent-1")
                    .processInstanceId("proc-1")
                    .taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_PARENT)
                    .build();
            ExternalTask child1 = ExternalTask.builder()
                    .id("child-1")
                    .processInstanceId("proc-1")
                    .taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD)
                    .coordinatorTaskId("parent-1")
                    .build();
            ExternalTask child2 = ExternalTask.builder()
                    .id("child-2")
                    .processInstanceId("proc-1")
                    .taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD)
                    .coordinatorTaskId("parent-1")
                    .build();
            ExternalTask unrelated = ExternalTask.builder()
                    .id("unrelated-1")
                    .processInstanceId("proc-1")
                    .taskDefinitionId("SOME_OTHER_TASK")
                    .build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(parent, child1, child2, unrelated), null, null, null, null, null, null, null, null, null, null));

            // Só a mãe é explicitamente apagada nesta transação — as filhas devem cair pela cascata.
            repository.commitWork(new UnitOfWork(null, null, null,
                    null, null, null, null, List.of("parent-1"), null, null, null, null, null, null, null));

            assertTrue(repository.findExternalTaskById("parent-1").isEmpty());
            assertTrue(repository.findExternalTaskById("child-1").isEmpty());
            assertTrue(repository.findExternalTaskById("child-2").isEmpty());
            assertTrue(repository.findExternalTaskById("unrelated-1").isPresent(),
                    "A tarefa sem coordinatorTaskId apontando para a mãe apagada não deveria ser afetada.");
        }

        @Test
        void resolveCorrelationChildForAllPolicyOnlySatisfiesOnTheLastRemainingKey() {
            ExternalTask parent = ExternalTask.builder()
                    .id("parent-all")
                    .processInstanceId("proc-2")
                    .taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_PARENT)
                    .pendingCorrelationKeys(List.of("KEY_A", "KEY_B"))
                    .build();
            ExternalTask childA = ExternalTask.builder()
                    .id("child-a").processInstanceId("proc-2").taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD).coordinatorTaskId("parent-all").correlationKey("KEY_A").build();
            ExternalTask childB = ExternalTask.builder()
                    .id("child-b").processInstanceId("proc-2").taskDefinitionId("WAIT_ALL")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD).coordinatorTaskId("parent-all").correlationKey("KEY_B").build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(parent, childA, childB), null, null, null, null, null, null, null, null, null, null));

            assertFalse(repository.resolveCorrelationChild("child-a", "parent-all", MatchPolicy.ALL),
                    "Ainda resta KEY_B pendente — não deveria satisfazer a política ALL.");
            assertEquals(ExternalTaskStatus.CORRELATED, repository.findExternalTaskById("child-a").orElseThrow().status());
            assertEquals(List.of("KEY_B"), repository.findExternalTaskById("parent-all").orElseThrow().pendingCorrelationKeys());

            assertTrue(repository.resolveCorrelationChild("child-b", "parent-all", MatchPolicy.ALL),
                    "KEY_B era a última chave pendente — deveria satisfazer a política ALL.");
        }

        @Test
        void resolveCorrelationChildForAnyPolicyOnlyTheFirstCallSatisfies() {
            ExternalTask parent = ExternalTask.builder()
                    .id("parent-any").processInstanceId("proc-3").taskDefinitionId("WAIT_ANY")
                    .type(ExternalTaskType.EVENT_CATCHER_PARENT).build();
            ExternalTask childA = ExternalTask.builder()
                    .id("child-a2").processInstanceId("proc-3").taskDefinitionId("WAIT_ANY")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD).coordinatorTaskId("parent-any").correlationKey("KEY_A").build();
            ExternalTask childB = ExternalTask.builder()
                    .id("child-b2").processInstanceId("proc-3").taskDefinitionId("WAIT_ANY")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD).coordinatorTaskId("parent-any").correlationKey("KEY_B").build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(parent, childA, childB), null, null, null, null, null, null, null, null, null, null));

            assertTrue(repository.resolveCorrelationChild("child-a2", "parent-any", MatchPolicy.ANY),
                    "Primeira chamada em ANY deveria satisfazer imediatamente.");
            assertFalse(repository.resolveCorrelationChild("child-b2", "parent-any", MatchPolicy.ANY),
                    "A mãe já está COMPLETED — uma segunda chegada não deveria satisfazer de novo.");
        }

        @Test
        void resolveCorrelationChildReturnsFalseWhenParentAlreadyGone() {
            ExternalTask orphanChild = ExternalTask.builder()
                    .id("orphan-child").processInstanceId("proc-4").taskDefinitionId("WAIT_ANY")
                    .type(ExternalTaskType.EVENT_CATCHER_CHILD).coordinatorTaskId("does-not-exist").correlationKey("KEY_A").build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(orphanChild), null, null, null, null, null, null, null, null, null, null));

            assertFalse(repository.resolveCorrelationChild("orphan-child", "does-not-exist", MatchPolicy.ANY),
                    "Mãe já removida (ex.: corrida perdida em ANY, ou timeout de boundary timer) — nada a satisfazer.");
        }

        @Test
        void findExternalTaskByCorrelationKeyIsScopedByTenantAndExcludesCorrelated() {
            ExternalTask tenantATask = ExternalTask.builder()
                    .id("task-tenant-a").processInstanceId("proc-5").taskDefinitionId("WAIT")
                    .tenantId("tenant-a").correlationKey("SAME_KEY").status(ExternalTaskStatus.CREATED).build();
            ExternalTask tenantBTask = ExternalTask.builder()
                    .id("task-tenant-b").processInstanceId("proc-6").taskDefinitionId("WAIT")
                    .tenantId("tenant-b").correlationKey("SAME_KEY").status(ExternalTaskStatus.CREATED).build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(tenantATask, tenantBTask), null, null, null, null, null, null, null, null, null, null));

            assertEquals("task-tenant-a", repository.findExternalTaskByCorrelationKey("SAME_KEY", "tenant-a").orElseThrow().id());
            assertEquals("task-tenant-b", repository.findExternalTaskByCorrelationKey("SAME_KEY", "tenant-b").orElseThrow().id());
            assertTrue(repository.findExternalTaskByCorrelationKey("SAME_KEY", "tenant-c").isEmpty());

            ExternalTask correlated = tenantATask.toBuilder().status(ExternalTaskStatus.CORRELATED).build();
            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(correlated), null, null, null, null, null, null, null, null, null, null));

            assertTrue(repository.findExternalTaskByCorrelationKey("SAME_KEY", "tenant-a").isEmpty(),
                    "Uma tarefa já CORRELATED não deveria ser encontrada de novo (idempotência).");
        }

        @Test
        void countExternalTasksByDefinitionIdExcludesCorrelatedChildren() {
            ExternalTask pendingChild = ExternalTask.builder()
                    .id("pending-child").processInstanceId("proc-7").taskDefinitionId("WAIT_ALL")
                    .status(ExternalTaskStatus.CREATED).build();
            ExternalTask correlatedChild = ExternalTask.builder()
                    .id("correlated-child").processInstanceId("proc-7").taskDefinitionId("WAIT_ALL")
                    .status(ExternalTaskStatus.CORRELATED).build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(pendingChild, correlatedChild), null, null, null, null, null, null, null, null, null, null));

            assertEquals(1, repository.countExternalTasksByDefinitionId("WAIT_ALL"));
        }
    }

    /**
     * Cobre o guard de finalização documentado em {@code UnitOfWork.finalizingNodeId}: a race entre um
     * boundary event interruptivo (timer ou catch event) disparando e o nó pai que ele vigia concluindo por
     * qualquer outro caminho — normal ou um segundo boundary event irmão — ao mesmo tempo.
     */
    @Nested
    class BoundaryEventFinalizationGuard {

        @Test
        void finalizingNodeIdDeletesGuardedParentAndCascadesSiblingBoundaryEventsButNotUnrelatedTasks() {
            ExternalTask parent = ExternalTask.builder()
                    .id("activity-1").processInstanceId("proc-1").taskDefinitionId("WAIT_FOR_APPROVAL").build();
            ExecutableTask timerSibling = ExecutableTask.builder()
                    .id("timer-1").processInstanceId("proc-1").taskDefinitionId("BOUNDARY_TIMER")
                    .type(ExecutableTaskType.INTERRUPTIVE_TIMER)
                    .attachedToRefId("activity-1").attachedToRefType(AttachedTaskType.EXTERNAL_TASK).build();
            ExternalTask catchSibling = ExternalTask.builder()
                    .id("catch-1").processInstanceId("proc-1").taskDefinitionId("BOUNDARY_CATCH")
                    .attachedToRefId("activity-1").attachedToRefType(AttachedTaskType.EXTERNAL_TASK).build();
            ExternalTask unrelated = ExternalTask.builder()
                    .id("unrelated-1").processInstanceId("proc-1").taskDefinitionId("SOME_OTHER_TASK").build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    List.of(timerSibling), List.of(parent, catchSibling, unrelated),
                    null, null, null, null, null, null, null, null, null, null));

            // Simula o boundary timer disparando: o pai é o finalizingNodeId, exatamente como
            // ContinuationService monta quando completedExecutableTask.attachedToRefId() != null.
            repository.commitWork(new UnitOfWork(null, null, null,
                    null, null, List.of("timer-1"), null, null, null, null, null, null,
                    null, null, null, "activity-1", AttachedTaskType.EXTERNAL_TASK));

            assertTrue(repository.findExternalTaskById("activity-1").isEmpty(), "O pai guardado deve ser apagado.");
            assertTrue(repository.findExternalTaskById("catch-1").isEmpty(),
                    "O irmão catch event deve cair pela cascata de attachedToRefId, mesmo sem estar em externalTasksToDelete.");
            assertTrue(repository.findExecutableTaskById("timer-1").isEmpty());
            assertTrue(repository.findExternalTaskById("unrelated-1").isPresent(),
                    "Uma tarefa sem attachedToRefId para o pai apagado não deveria ser afetada.");
        }

        @Test
        void finalizingNodeIdOnAlreadyGoneNodeThrowsAndAppliesNothingElseInTheSameCommit() {
            ExecutableTask bystanderTask = ExecutableTask.builder()
                    .id("bystander-1").processInstanceId("proc-2").taskDefinitionId("NODE_B")
                    .status(ExecutableTaskStatus.PENDING).build();

            UnitOfWork uow = new UnitOfWork(null, null, null,
                    List.of(bystanderTask), null, null, null, null, null, null, null, null,
                    null, null, null, "vanished-1", AttachedTaskType.EXECUTABLE_TASK);

            assertThrows(OptimisticLockingFailureException.class, () -> repository.commitWork(uow));

            assertTrue(repository.findExecutableTaskById("bystander-1").isEmpty(),
                    "O guard falhou antes de qualquer outra escrita — nada mais deste commit deveria ter sido aplicado.");
        }

        @Test
        void secondFinalizationAttemptOnTheSameParentLosesAfterTheFirstWins() {
            ExternalTask parent = ExternalTask.builder()
                    .id("shared-parent-1").processInstanceId("proc-3").taskDefinitionId("WAIT_FOR_APPROVAL").build();

            repository.commitWork(new UnitOfWork(null, null, null,
                    null, List.of(parent), null, null, null, null, null, null, null, null, null, null));

            // Primeiro boundary event (ex.: o timer) vence a corrida.
            repository.commitWork(new UnitOfWork(null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, "shared-parent-1", AttachedTaskType.EXTERNAL_TASK));

            // Segundo boundary event irmão (ex.: o catch event), que já tinha lido o pai antes do primeiro
            // commitar e montou sua própria continuação em memória sem saber disso — deve perder.
            UnitOfWork secondAttempt = new UnitOfWork(null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, "shared-parent-1", AttachedTaskType.EXTERNAL_TASK);

            assertThrows(OptimisticLockingFailureException.class, () -> repository.commitWork(secondAttempt),
                    "O pai já foi finalizado pelo primeiro boundary event — o segundo não deveria conseguir prosseguir.");
        }
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
