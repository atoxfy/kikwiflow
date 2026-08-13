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
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Complementa {@link NonInterruptiveTimerRecurrenceTest} (que cobre o laço indefinido, sem bound) provando o
 * comportamento de {@code SchedulePolicy.maxOccurrences}: quando declarado, o laço de recorrência do
 * {@code BOUNDARY_NON_INTERRUPTIVE_TIMER} se encerra sozinho após aquele número de ciclos — mesmo sinal já
 * usado por {@code FIXED_DATES} esgotada (ver {@code TimerDueDateEvaluator.calculateNextSchedule}), sem
 * cancelar nem tocar o nó pai (ver docs/engine/04-timers-e-agendamento.md).
 */
@DisplayName("Dado um BOUNDARY_NON_INTERRUPTIVE_TIMER (RATE_DURATION, maxOccurrences=2) anexado a um EXTERNAL_TASK")
class NonInterruptiveTimerMaxOccurrencesTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/non-interruptive-timer-recurrence-bounded.json");
    }

    private List<ExecutableTask> findRecurringPings(String processInstanceId) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> "RECURRING_PING".equals(t.taskDefinitionId()))
                .toList();
    }

    @Test
    @DisplayName("O 1º ciclo já nasce com occurrence=1")
    void theFirstCycleIsCreatedWithOccurrenceOne() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-MAX-1")
                .execute();

        List<ExecutableTask> firstCycle = findRecurringPings(instance.id());
        assertEquals(1, firstCycle.size());
        assertEquals(1, firstCycle.get(0).occurrence());
    }

    @Test
    @DisplayName("O laço dispara exatamente maxOccurrences vezes e depois para sozinho, sem tocar o PARENT_WAIT nem deixar ExecutableTask órfã")
    void theLoopStopsAfterMaxOccurrencesWithoutTouchingTheParent() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-NIT-MAX-2")
                .execute();

        ExecutableTask firstCycle = findRecurringPings(instance.id()).get(0);
        testEngine.engine().executeFromTask(firstCycle);

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "PARENT_WAIT");

        List<ExecutableTask> secondCycle = findRecurringPings(instance.id());
        assertEquals(1, secondCycle.size(), "Ainda deveria haver o 2º ciclo pendente (maxOccurrences=2).");
        assertEquals(2, secondCycle.get(0).occurrence());

        testEngine.engine().executeFromTask(secondCycle.get(0));

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "PARENT_WAIT");
        assertTrue(findRecurringPings(instance.id()).isEmpty(),
                "Depois do 2º ciclo (maxOccurrences=2), o laço deveria ter parado sem criar um 3º ciclo.");
    }
}
