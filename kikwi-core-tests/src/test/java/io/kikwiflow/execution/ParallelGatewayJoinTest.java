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

package io.kikwiflow.execution;

import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o fan-out/fan-in de {@code PARALLEL_GATEWAY}/{@code JOIN_GATEWAY}: a continuação de um
 * {@code PARALLEL_GATEWAY} é sempre assíncrona ({@code Navigator.determineNextContinuation} força
 * {@code isAsynchronous=true} para esse tipo de nó, ver {@code ProcessExecutionManager}) — ou seja, mesmo um ramo
 * cujo {@code EXECUTABLE_TASK} declara {@code commitBefore: false} não roda inline no momento do split: ambos os
 * ramos viram {@code ExecutableTask} persistidas, e cada uma só executa quando retomada individualmente via
 * {@code executeFromTask}. O {@code JOIN_GATEWAY} correspondente é criado antecipadamente com status
 * {@code AWAITING_BRANCHES} e só passa para {@code PENDING} (liberado para execução) quando a última ramificação
 * pendente é resolvida (ver {@code InMemoryKikwiEngineRepository.resolveBranchPull}).
 */
@DisplayName("Dado um processo com um PARALLEL_GATEWAY dividindo em duas ramificações")
class ParallelGatewayJoinTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final AtomicBoolean fastBranchRan = new AtomicBoolean(false);
    private final AtomicBoolean slowBranchRan = new AtomicBoolean(false);
    private final AtomicBoolean afterJoinRan = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withTaskHandler("fastBranchHandler", ctx -> fastBranchRan.set(true))
                .withTaskHandler("slowBranchHandler", ctx -> slowBranchRan.set(true))
                .withTaskHandler("afterJoinHandler", ctx -> afterJoinRan.set(true))
                .build();

        definition = testEngine.deploy("/processes/parallel-gateway-fan-out-join.json");
    }

    private ExecutableTask findTaskByDefinitionId(String processInstanceId, String taskDefinitionId) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Esperava uma ExecutableTask para '" + taskDefinitionId + "'."));
    }

    @Test
    @DisplayName("Quando o processo inicia, ambas as ramificações são persistidas como PENDING e nenhum handler roda inline, mesmo a de commitBefore=false")
    void startingTheProcessPersistsBothBranchesWithoutRunningEitherHandlerInline() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-PG-1")
                .execute();

        assertFalse(fastBranchRan.get(), "FAST_TASK (commitBefore=false) não deveria ter rodado inline no split.");
        assertFalse(slowBranchRan.get(), "SLOW_TASK (commitBefore=true) não deveria ter rodado inline no split.");
        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

        List<ExecutableTask> tasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertEquals(3, tasks.size(), "Esperava 3 ExecutableTasks: FAST_TASK, SLOW_TASK e o JOIN_GATEWAY antecipado.");

        ExecutableTask fastTask = findTaskByDefinitionId(instance.id(), "FAST_TASK");
        assertEquals(ExecutableTaskStatus.PENDING, fastTask.status());
        assertEquals(ExecutableTaskType.STANDARD, fastTask.type());

        ExecutableTask slowTask = findTaskByDefinitionId(instance.id(), "SLOW_TASK");
        assertEquals(ExecutableTaskStatus.PENDING, slowTask.status());
        assertEquals(ExecutableTaskType.STANDARD, slowTask.type());

        assertTrue(!fastTask.branchId().equals(slowTask.branchId()), "Cada ramificação deveria ganhar seu próprio branchId.");

        ExecutableTask joinTask = findTaskByDefinitionId(instance.id(), "JOIN_SYNC");
        assertEquals(ExecutableTaskType.JOIN_GATEWAY, joinTask.type());
        assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, joinTask.status());
        assertEquals(2, joinTask.pendingBranchIds().size());
        assertTrue(joinTask.pendingBranchIds().containsAll(List.of(fastTask.branchId(), slowTask.branchId())));
    }

    @Nested
    @DisplayName("Quando as duas ramificações são concluídas")
    class WhenBothBranchesComplete {

        @Test
        @DisplayName("Concluindo FAST_TASK antes de SLOW_TASK, o join só libera após a segunda, e então continua sincronamente")
        void completingFastThenSlowReleasesTheJoinOnlyAfterBoth() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-PG-2")
                    .execute();

            ExecutableTask fastTask = findTaskByDefinitionId(instance.id(), "FAST_TASK");
            testEngine.engine().executeFromTask(fastTask);

            assertTrue(fastBranchRan.get());
            assertFalse(slowBranchRan.get(), "SLOW_TASK ainda não foi retomada — seu handler não deveria ter rodado.");
            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

            ExecutableTask joinAfterFirst = findTaskByDefinitionId(instance.id(), "JOIN_SYNC");
            assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, joinAfterFirst.status(),
                    "O join não deveria liberar ainda — falta a ramificação SLOW_TASK.");
            assertEquals(1, joinAfterFirst.pendingBranchIds().size());

            ExecutableTask slowTask = findTaskByDefinitionId(instance.id(), "SLOW_TASK");
            testEngine.engine().executeFromTask(slowTask);

            assertTrue(slowBranchRan.get());
            assertFalse(afterJoinRan.get(), "O join foi liberado (virou PENDING) mas ainda não foi retomado — AFTER_JOIN_TASK não deveria ter rodado.");
            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

            ExecutableTask joinAfterBoth = findTaskByDefinitionId(instance.id(), "JOIN_SYNC");
            assertEquals(ExecutableTaskStatus.PENDING, joinAfterBoth.status(),
                    "Com as duas ramificações resolvidas, o join deveria estar liberado (PENDING).");

            ProcessInstance completed = testEngine.engine().executeFromTask(joinAfterBoth);

            assertTrue(afterJoinRan.get(), "Retomar o JOIN_GATEWAY liberado deveria seguir sincronamente até AFTER_JOIN_TASK.");
            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }

        @Test
        @DisplayName("A ordem inversa (SLOW_TASK antes de FAST_TASK) produz o mesmo resultado final")
        void completingSlowThenFastAlsoReachesCompletion() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-PG-3")
                    .execute();

            ExecutableTask slowTask = findTaskByDefinitionId(instance.id(), "SLOW_TASK");
            testEngine.engine().executeFromTask(slowTask);

            assertTrue(slowBranchRan.get());
            assertFalse(fastBranchRan.get());
            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

            ExecutableTask fastTask = findTaskByDefinitionId(instance.id(), "FAST_TASK");
            testEngine.engine().executeFromTask(fastTask);

            assertTrue(fastBranchRan.get());

            ExecutableTask joinAfterBoth = findTaskByDefinitionId(instance.id(), "JOIN_SYNC");
            assertEquals(ExecutableTaskStatus.PENDING, joinAfterBoth.status());

            ProcessInstance completed = testEngine.engine().executeFromTask(joinAfterBoth);

            assertTrue(afterJoinRan.get());
            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }
    }
}
