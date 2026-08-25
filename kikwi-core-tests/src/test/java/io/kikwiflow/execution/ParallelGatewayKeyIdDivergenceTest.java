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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trava a correção descrita em docs/engine/15-achados-motor-lacunas-de-validacao.md, §2.2: a engine grava
 * {@code taskDefinitionId} usando exclusivamente a CHAVE de {@code flowNodes}, nunca o campo {@code id()}
 * interno do nó. Este fixture ({@code parallel-gateway-key-id-divergence.json}) declara todo nó com um
 * {@code id} interno deliberadamente diferente da chave usada em {@code flowNodes} — antes da correção, o
 * split/join gravava {@code taskDefinitionId} a partir de {@code node.id()} (ex.: "ID_BRANCH_A_INTERNO"),
 * enquanto todo lookup de retomada (via {@code executeFromTask}) resolve pela chave (ex.: "BRANCH_A") — a
 * divergência quebrava a sincronização do join silenciosamente. Este teste falha antes da correção e passa
 * depois dela.
 */
@DisplayName("Dado um processo cuja chave em flowNodes diverge do campo id interno de cada nó")
class ParallelGatewayKeyIdDivergenceTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final AtomicBoolean branchARan = new AtomicBoolean(false);
    private final AtomicBoolean branchBRan = new AtomicBoolean(false);
    private final AtomicBoolean afterJoinRan = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withTaskHandler("branchAHandler", ctx -> branchARan.set(true))
                .withTaskHandler("branchBHandler", ctx -> branchBRan.set(true))
                .withTaskHandler("afterJoinHandler", ctx -> afterJoinRan.set(true))
                .build();

        definition = testEngine.deploy("/processes/parallel-gateway-key-id-divergence.json");
    }

    private ExecutableTask findTaskByDefinitionId(String processInstanceId, String taskDefinitionId) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Esperava uma ExecutableTask com taskDefinitionId '" + taskDefinitionId
                        + "' (a CHAVE do mapa flowNodes, não o campo id() interno do nó)."));
    }

    @Test
    @DisplayName("O split persiste as duas ramificações e o join antecipado com taskDefinitionId igual à chave do mapa, não ao id interno")
    void splitPersistsTasksKeyedByFlowNodesMapKey() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-DIV-1")
                .execute();

        List<ExecutableTask> tasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertEquals(3, tasks.size(), "Esperava 3 ExecutableTasks: BRANCH_A, BRANCH_B e o JOIN_GATEWAY antecipado.");

        // findTaskByDefinitionId busca por "BRANCH_A"/"BRANCH_B"/"JOIN" (as CHAVES do mapa) — se a engine ainda
        // gravasse taskDefinitionId a partir de node.id() ("ID_BRANCH_A_INTERNO" etc.), essas buscas falhariam.
        ExecutableTask branchA = findTaskByDefinitionId(instance.id(), "BRANCH_A");
        assertEquals(ExecutableTaskStatus.PENDING, branchA.status());
        assertEquals(ExecutableTaskType.STANDARD, branchA.type());

        ExecutableTask branchB = findTaskByDefinitionId(instance.id(), "BRANCH_B");
        assertEquals(ExecutableTaskStatus.PENDING, branchB.status());

        ExecutableTask joinTask = findTaskByDefinitionId(instance.id(), "JOIN");
        assertEquals(ExecutableTaskType.JOIN_GATEWAY, joinTask.type());
        assertEquals(ExecutableTaskStatus.AWAITING_BRANCHES, joinTask.status());
        assertEquals(2, joinTask.pendingBranchIds().size());
        assertTrue(joinTask.pendingBranchIds().containsAll(List.of(branchA.branchId(), branchB.branchId())));
    }

    @Test
    @DisplayName("Concluindo as duas ramificações, o join libera e a instância chega ao fim mesmo com id interno divergente da chave")
    void completingBothBranchesReachesCompletionDespiteKeyIdDivergence() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-DIV-2")
                .execute();

        ExecutableTask branchA = findTaskByDefinitionId(instance.id(), "BRANCH_A");
        testEngine.engine().executeFromTask(branchA);
        assertTrue(branchARan.get());

        ExecutableTask branchB = findTaskByDefinitionId(instance.id(), "BRANCH_B");
        testEngine.engine().executeFromTask(branchB);
        assertTrue(branchBRan.get());

        ExecutableTask joinReleased = findTaskByDefinitionId(instance.id(), "JOIN");
        assertEquals(ExecutableTaskStatus.PENDING, joinReleased.status(),
                "Com as duas ramificações resolvidas (pela chave do mapa), o join deveria estar liberado.");

        ProcessInstance completed = testEngine.engine().executeFromTask(joinReleased);

        assertTrue(afterJoinRan.get(), "Retomar o JOIN_GATEWAY liberado deveria seguir sincronamente até AFTER_JOIN.");
        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }
}
