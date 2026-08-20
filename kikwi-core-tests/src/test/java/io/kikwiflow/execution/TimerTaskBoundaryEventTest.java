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

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code boundaryEventIds} em {@code TIMER_TASK} — nó de fluxo principal (não um evento de borda),
 * que hoje aceita {@code BOUNDARY_INTERRUPTIVE_TIMER}, {@code BOUNDARY_NON_INTERRUPTIVE_TIMER} e
 * {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT} (não {@code BOUNDARY_ERROR_HANDLER} — este nó não roda handler
 * síncrono nenhum). Seguro por natureza: {@code TimerTaskDefinition} não implementa {@code Executable}, então
 * não há efeito colateral em voo pra proteger — a mesma restrição que bloqueia boundary interruptivo num
 * {@code EXECUTABLE_TASK} de verdade não se aplica aqui (ver Javadoc de {@code TimerTaskDefinition} e
 * docs/engine/18-timer-task.md).
 *
 * <p>Mecanicamente, isso é o mesmo dispatch genérico ({@code ContinuationService.generateBoundaryEvents}) e a
 * mesma política de deploy-time ({@code DeployValidator.validateBoundaryEvents}) já usados por
 * {@code EXTERNAL_TASK}/{@code EXECUTABLE_TASK}/{@code EVENT_CATCHER}/{@code CALL_ACTIVITY_COORDINATOR} — só
 * uma allowlist nova, sem nenhum código novo de materialização.
 */
@DisplayName("Dado um TIMER_TASK com boundary events anexados")
class TimerTaskBoundaryEventTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/timer-task-with-boundary-events.json");
    }

    private ExecutableTask findTaskByDefinitionId(String processInstanceId, String taskDefinitionId) {
        return testEngine.repository().findExecutableTasksByProcessInstanceId(processInstanceId).stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Esperava uma ExecutableTask para '" + taskDefinitionId + "'."));
    }

    @Test
    @DisplayName("Disparar o BOUNDARY_INTERRUPTIVE_TIMER cancela o WAIT_SLA e os dois boundary irmãos ainda não disparados")
    void firingTheInterruptiveTimerCancelsParentAndUnfiredSiblings() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TT-BOUNDARY-1")
                .onTenant("tenant-a")
                .execute();

        ExecutableTask slaTimeout = findTaskByDefinitionId(instance.id(), "SLA_TIMEOUT");
        ProcessInstance afterTimeout = testEngine.engine().executeFromTask(slaTimeout);

        assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());

        // WAIT_SLA e o irmão SLA_PING (não-interruptivo) somem junto — nenhuma task deles sobra no banco.
        List<ExecutableTask> remaining = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertTrue(remaining.stream().noneMatch(t -> "WAIT_SLA".equals(t.taskDefinitionId())));
        assertTrue(remaining.stream().noneMatch(t -> "SLA_PING".equals(t.taskDefinitionId())));

        // O irmão catch event (SLA_CANCEL) some junto — correlacionar a chave depois lança.
        assertThrows(TaskNotFoundException.class, () ->
                        testEngine.engine().correlateMessage("cancelar-timer-task-sla", Map.of(), IDENTITY),
                "O catch event irmão deveria ter sido cancelado junto com o timeout.");
    }

    @Test
    @DisplayName("Disparar o BOUNDARY_NON_INTERRUPTIVE_TIMER recorre sem cancelar o WAIT_SLA nem os irmãos")
    void firingTheNonInterruptiveTimerRecursWithoutCancellingParentOrSiblings() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TT-BOUNDARY-2")
                .onTenant("tenant-a")
                .execute();

        ExecutableTask slaPing = findTaskByDefinitionId(instance.id(), "SLA_PING");
        ProcessInstance afterPing = testEngine.engine().executeFromTask(slaPing);

        // O processo principal segue intacto — o ping é só o próprio ciclo privado do timer não-interruptivo
        // concluindo (ver mesma semântica documentada em NonInterruptiveTimerRecurrenceTest).
        assertEquals(ProcessInstanceStatus.ACTIVE, afterPing.status());
        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

        // WAIT_SLA continua vivo, e um novo ciclo de SLA_PING foi reagendado (id diferente do disparado).
        ExecutableTask waitSla = findTaskByDefinitionId(instance.id(), "WAIT_SLA");
        assertEquals("WAIT_SLA", waitSla.taskDefinitionId());
        ExecutableTask nextPingCycle = findTaskByDefinitionId(instance.id(), "SLA_PING");
        assertTrue(!slaPing.id().equals(nextPingCycle.id()), "O próximo ciclo deveria ser uma task nova, não a mesma reaproveitada.");

        // O irmão interruptivo (SLA_TIMEOUT) e o catch event (SLA_CANCEL) seguem intactos.
        assertTrue(testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id())
                .stream().anyMatch(t -> "SLA_TIMEOUT".equals(t.taskDefinitionId())));
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "SLA_CANCEL");
    }

    @Test
    @DisplayName("Correlacionar o BOUNDARY_INTERRUPTIVE_CATCH_EVENT cancela o WAIT_SLA e os dois boundary irmãos")
    void correlatingTheCatchEventCancelsParentAndSiblings() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TT-BOUNDARY-3")
                .onTenant("tenant-a")
                .execute();

        ProcessInstance afterCorrelation = testEngine.engine().correlateMessage(
                "cancelar-timer-task-sla", Map.of(), IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, afterCorrelation.status());

        List<ExecutableTask> remaining = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertTrue(remaining.stream().noneMatch(t -> "WAIT_SLA".equals(t.taskDefinitionId())));
        assertTrue(remaining.stream().noneMatch(t -> "SLA_PING".equals(t.taskDefinitionId())),
                "O boundary não-interruptivo irmão deveria ter sido cancelado junto.");
        assertTrue(remaining.stream().noneMatch(t -> "SLA_TIMEOUT".equals(t.taskDefinitionId())),
                "O boundary interruptivo irmão deveria ter sido cancelado junto.");
    }

    @Test
    @DisplayName("Completar o WAIT_SLA normalmente (sem nenhum boundary disparar) limpa os três boundary events ainda pendentes")
    void completingTheParentNormallyCleansUpAllThreeUnfiredSiblings() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-TT-BOUNDARY-4")
                .onTenant("tenant-a")
                .execute();

        ExecutableTask waitSla = findTaskByDefinitionId(instance.id(), "WAIT_SLA");
        ProcessInstance afterFiring = testEngine.engine().executeFromTask(waitSla);

        assertEquals(ProcessInstanceStatus.ACTIVE, afterFiring.status());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "AFTER_TIMER");

        List<ExecutableTask> remaining = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertTrue(remaining.stream().noneMatch(t -> "SLA_TIMEOUT".equals(t.taskDefinitionId())));
        assertTrue(remaining.stream().noneMatch(t -> "SLA_PING".equals(t.taskDefinitionId())));
        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("cancelar-timer-task-sla", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Deploy rejeita um BOUNDARY_ERROR_HANDLER anexado a um TIMER_TASK")
    void deployRejectsAnErrorHandlerAttachedToATimerTask() {
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                testEngine.deploy("/processes/timer-task-boundary-error-handler-invalid.json"));
        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }
}
