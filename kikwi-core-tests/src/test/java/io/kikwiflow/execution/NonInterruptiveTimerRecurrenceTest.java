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
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o laço de recorrência de {@code BOUNDARY_NON_INTERRUPTIVE_TIMER} (ver
 * docs/engine/06-execucao-sincrona-assincrona.md, seção "Timers não-interruptivos se reagendam sozinhos"):
 * quando a {@code ExecutableTask} do tipo {@code NON_INTERRUPTIVE_TIMER} é retomada via
 * {@code executeFromTask}, {@code ContinuationService} calcula a próxima ocorrência via
 * {@code TimerDueDateEvaluator.calculateNextSchedule} e injeta uma nova {@code ExecutableTask} já agendada
 * para o próximo ciclo — sem cancelar nem tocar o nó pai ({@code attachedToRefId}), ao contrário de um timer
 * INTERRUPTIVO. O motor em si é o scheduler: não há dependência de nenhum scheduler externo.
 */
@DisplayName("Dado um EXTERNAL_TASK com um boundary BOUNDARY_NON_INTERRUPTIVE_TIMER (RATE_DURATION) anexado")
class NonInterruptiveTimerRecurrenceTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/non-interruptive-timer-recurrence.json");
    }

    private ExecutableTask findRecurringPing(String processInstanceId) {
        List<ExecutableTask> tasks = testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> "RECURRING_PING".equals(t.taskDefinitionId()))
                .toList();
        assertEquals(1, tasks.size(), "Esperava exatamente um ciclo pendente do timer não-interruptivo.");
        return tasks.get(0);
    }

    @Test
    @DisplayName("Ao iniciar, cria o PARENT_WAIT ativo e um primeiro ciclo do timer não-interruptivo PENDING")
    void startingTheProcessCreatesTheParentTaskAndTheFirstTimerCycle() {
        Instant before = Instant.now();

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-1")
                .execute();

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "PARENT_WAIT");

        ExecutableTask firstCycle = findRecurringPing(instance.id());
        assertEquals(ExecutableTaskType.NON_INTERRUPTIVE_TIMER, firstCycle.type());
        assertEquals(ExecutableTaskStatus.PENDING, firstCycle.status());
        assertTrue(firstCycle.dueDate().isAfter(before.plus(java.time.Duration.ofMinutes(55))),
                "dueDate deveria ser aproximadamente now + PT1H, mas foi " + firstCycle.dueDate());
    }

    @Test
    @DisplayName("Disparar um ciclo reagenda o próximo automaticamente, sem cancelar nem alterar o PARENT_WAIT")
    void firingACycleReschedulesTheNextOneWithoutTouchingTheParent() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-2")
                .execute();

        ExecutableTask firstCycle = findRecurringPing(instance.id());
        Instant firstDueDate = firstCycle.dueDate();

        ProcessInstance afterFiring = testEngine.engine().executeFromTask(firstCycle);

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        assertEquals(afterFiring.id(), instance.id());
        // PARENT_WAIT segue intocado — um timer não-interruptivo nunca cancela o nó ao qual está anexado.
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "PARENT_WAIT");

        ExecutableTask secondCycle = findRecurringPing(instance.id());
        assertNotEquals(firstCycle.id(), secondCycle.id(), "O ciclo disparado deveria ter sido substituído por um novo, não reaproveitado.");
        assertEquals(ExecutableTaskStatus.PENDING, secondCycle.status());
        assertTrue(!secondCycle.dueDate().isBefore(firstDueDate),
                "O próximo ciclo deveria ter um dueDate igual ou mais distante no futuro que o ciclo disparado.");
    }

    @Test
    @DisplayName("O laço se repete por múltiplos ciclos consecutivos, sempre preservando o PARENT_WAIT ativo")
    void theLoopRepeatsAcrossMultipleConsecutiveCycles() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-3")
                .execute();

        for (int cycle = 0; cycle < 3; cycle++) {
            ExecutableTask current = findRecurringPing(instance.id());
            testEngine.engine().executeFromTask(current);
            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "PARENT_WAIT");
        }

        // Depois de 3 disparos consecutivos, ainda há exatamente um ciclo pendente aguardando o próximo dueDate.
        findRecurringPing(instance.id());
    }

    @Test
    @DisplayName("Completar o PARENT_WAIT normalmente remove o timer não-interruptivo anexado, sem deixar ExecutableTask órfã")
    void completingTheParentNormallyCleansUpTheAttachedNonInterruptiveTimer() {
        io.kikwiflow.model.security.IdentityContext identity = new io.kikwiflow.model.security.IdentityContext("test-actor", "tenant-a");

        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-4")
                .onTenant("tenant-a")
                .execute();

        String parentWaitId = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).stream()
                .filter(t -> "PARENT_WAIT".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow()
                .id();

        ProcessInstance afterParentCompletes = testEngine.engine().completeExternalTask(parentWaitId, java.util.Map.of(), identity);

        // O processo segue ativo (há um EXTERNAL_TASK depois de PARENT_WAIT) — só assim dá pra provar que a
        // limpeza do timer não-interruptivo não é apenas efeito colateral do wipe em bloco de uma instância
        // concluída.
        testEngine.repository().assertThatProcessInstanceIsActive(afterParentCompletes.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "AFTER_WAIT");
        assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id())
                        .stream().noneMatch(t -> "RECURRING_PING".equals(t.taskDefinitionId())),
                "O timer não-interruptivo anexado ao PARENT_WAIT deveria ter sido removido após a conclusão normal do pai.");

        String afterWaitId = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).stream()
                .filter(t -> "AFTER_WAIT".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow()
                .id();

        ProcessInstance completed = testEngine.engine().completeExternalTask(afterWaitId, java.util.Map.of(), identity);

        assertEquals(io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus.COMPLETED, completed.status());
        testEngine.repository().assertThatProcessInstanceIsCompleted(instance.id());
    }
}
