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
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code TIMER_TASK}: um nó de fluxo principal (não um evento de borda) que pausa até um {@code
 * dueDate} calculado (mesmo mecanismo de {@code BOUNDARY_INTERRUPTIVE_TIMER}: STATIC/VARIABLE/BEAN) e então
 * continua pelas próprias arestas de saída. Materializado como {@code ExecutableTask} (não {@code
 * ExternalTask}) — a espera é sempre assíncrona, independente do {@code commitBefore} declarado no {@code
 * .kikwi} (ver {@code ProcessExecutionManager.isCommitBefore}).
 */
@DisplayName("Dado um processo com um TIMER_TASK aguardando o dueDate")
class TimerTaskTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    private TestEngine testEngine;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
    }

    @Test
    @DisplayName("Ao iniciar, cria uma ExecutableTask PENDING com o dueDate resolvido, mesmo com commitBefore=false no .kikwi")
    void startingTheProcessCreatesAPendingTimerTaskRegardlessOfCommitBeforeInJson() {
        ProcessDefinition definition = testEngine.deploy("/processes/timer-task-static.json");
        Instant before = Instant.now();

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TIMER-TASK-1")
                .onTenant("tenant-a")
                .execute();

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

        List<ExecutableTask> executableTasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        ExecutableTask timerTask = executableTasks.stream()
                .filter(t -> "WAIT_SLA".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow();

        assertEquals(ExecutableTaskType.TIMER_TASK, timerTask.type());
        assertEquals(ExecutableTaskStatus.PENDING, timerTask.status());
        assertTrue(timerTask.dueDate().isAfter(before.plus(Duration.ofMinutes(55))),
                "dueDate deveria ser aproximadamente now + PT1H (STATIC), mas foi " + timerTask.dueDate());
    }

    @Test
    @DisplayName("Disparar o timer avança para a saída do TIMER_TASK, não completa o processo sozinho")
    void firingTheTimerTaskContinuesToItsOutgoingNode() {
        ProcessDefinition definition = testEngine.deploy("/processes/timer-task-static.json");

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TIMER-TASK-2")
                .onTenant("tenant-a")
                .execute();

        ExecutableTask timerTask = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                .filter(t -> "WAIT_SLA".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow();

        ProcessInstance afterFiring = testEngine.engine().executeFromTask(timerTask);

        assertEquals(ProcessInstanceStatus.ACTIVE, afterFiring.status());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "AFTER_TIMER");
        testEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "WAIT_SLA");

        String afterTimerTaskId = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).get(0).id();
        ProcessInstance completed = testEngine.engine().completeExternalTask(afterTimerTaskId, Map.of(), IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }

    @Test
    @DisplayName("providerType VARIABLE resolve o dueDate a partir de uma variável de processo")
    void variableProviderResolvesDueDateFromProcessVariable() {
        ProcessDefinition definition = testEngine.deploy("/processes/timer-task-variable.json");
        Instant before = Instant.now();

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TIMER-TASK-3")
                .onTenant("tenant-a")
                .withVariables(Map.of("slaDuration", new ProcessVariable("slaDuration", "PT30M")))
                .execute();

        ExecutableTask timerTask = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).stream()
                .filter(t -> "WAIT_SLA".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow();

        assertTrue(timerTask.dueDate().isAfter(before.plus(Duration.ofMinutes(25)))
                        && timerTask.dueDate().isBefore(before.plus(Duration.ofMinutes(35))),
                "dueDate deveria ser aproximadamente now + PT30M, mas foi " + timerTask.dueDate());

        ProcessInstance completed = testEngine.engine().executeFromTask(timerTask);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }
}
