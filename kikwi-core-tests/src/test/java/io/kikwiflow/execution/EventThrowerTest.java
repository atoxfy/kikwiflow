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

import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o nó {@code EVENT_THROWER} (throw correlacionado): resolve uma chave de correlação e entrega
 * internamente pelo mesmo caminho de {@code KikwiflowEngine.correlateMessage}, sem exigir que o processo que
 * lança conheça o {@code processInstanceId}/{@code taskId} de quem está esperando.
 *
 * <p>Cobre especificamente os dois pontos de configurabilidade discutidos para o limite transacional entre
 * quem lança e quem recebe: {@code commitAfter} no EVENT_CATCHER alvo (decide se a continuação depois da
 * correlação roda inline ou fica pendente para retomada assíncrona) e {@code commitBefore} no próprio
 * EVENT_THROWER (decide se o throw em si roda inline ou vira uma ExecutableTask EVENT_THROW pendente).
 */
@DisplayName("Dado um processo com um EVENT_THROWER lançando um evento correlacionado")
class EventThrowerTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    @Test
    @DisplayName("Com commitAfter=false no EVENT_CATCHER, o throw completa a espera e a continuação roda inline, na mesma chamada")
    void throwCompletesWaitingCatcherSynchronouslyWhenCatcherCommitAfterIsFalse() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("afterCatchHandler", ctx -> handlerInvocations.incrementAndGet())
                .build();

        ProcessDefinition catcherDefinition = testEngine.deploy("/processes/event-catcher-for-throw-sync.json");
        ProcessDefinition throwerDefinition = testEngine.deploy("/processes/event-thrower-standalone.json");

        ProcessInstance catcherInstance = testEngine.engine().startProcess()
                .byKey(catcherDefinition.key())
                .withBusinessKey("BK-CATCHER-SYNC-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1001")))
                .execute();

        testEngine.engine().startProcess()
                .byKey(throwerDefinition.key())
                .withBusinessKey("BK-THROWER-SYNC-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1001")))
                .execute();

        assertEquals(1, handlerInvocations.get(),
                "O handler depois do catcher deveria ter rodado inline, na mesma chamada que iniciou o processo lançador.");
        testEngine.repository().assertThatProcessInstanceIsCompleted(catcherInstance.id());
    }

    @Test
    @DisplayName("Com commitAfter=true no EVENT_CATCHER, o throw completa a espera mas a continuação fica pendente para retomada assíncrona")
    void throwCompletesWaitingCatcherButDefersContinuationWhenCatcherCommitAfterIsTrue() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("afterCatchHandler", ctx -> handlerInvocations.incrementAndGet())
                .build();

        ProcessDefinition catcherDefinition = testEngine.deploy("/processes/event-catcher-for-throw-async.json");
        ProcessDefinition throwerDefinition = testEngine.deploy("/processes/event-thrower-standalone.json");

        ProcessInstance catcherInstance = testEngine.engine().startProcess()
                .byKey(catcherDefinition.key())
                .withBusinessKey("BK-CATCHER-ASYNC-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1002")))
                .execute();

        testEngine.engine().startProcess()
                .byKey(throwerDefinition.key())
                .withBusinessKey("BK-THROWER-ASYNC-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1002")))
                .execute();

        assertEquals(0, handlerInvocations.get(),
                "commitAfter=true no catcher deveria impedir que a continuação (o handler) rodasse inline durante o throw.");
        testEngine.repository().assertThatProcessInstanceIsActive(catcherInstance.id());
        testEngine.repository().assertHasntActiveExternalTaskOn(catcherInstance.id(), "WAIT_ORDER_EVENT");

        List<ExecutableTask> pending = testEngine.repository().findExecutableTasksByProcessInstanceId(catcherInstance.id());
        assertEquals(1, pending.size());
        ExecutableTask afterCatchTask = pending.get(0);
        assertEquals("AFTER_CATCH_TASK", afterCatchTask.taskDefinitionId());
        assertEquals(ExecutableTaskType.STANDARD, afterCatchTask.type());

        ProcessInstance afterResuming = testEngine.engine().executeFromTask(afterCatchTask);

        assertEquals(1, handlerInvocations.get(), "Só depois de retomar a ExecutableTask pendente o handler deveria rodar.");
        assertEquals(ProcessInstanceStatus.COMPLETED, afterResuming.status());
    }

    @Test
    @DisplayName("Com commitBefore=true no EVENT_THROWER, o throw em si vira uma ExecutableTask EVENT_THROW pendente em vez de disparar inline")
    void throwerWithCommitBeforeIsPersistedAsPendingTaskInsteadOfFiringInline() {
        AtomicInteger handlerInvocations = new AtomicInteger();
        TestEngine testEngine = SingletonsFactory.engine()
                .withTaskHandler("afterCatchHandler", ctx -> handlerInvocations.incrementAndGet())
                .build();

        ProcessDefinition catcherDefinition = testEngine.deploy("/processes/event-catcher-for-throw-sync.json");
        ProcessDefinition throwerDefinition = testEngine.deploy("/processes/event-thrower-standalone-async.json");

        ProcessInstance catcherInstance = testEngine.engine().startProcess()
                .byKey(catcherDefinition.key())
                .withBusinessKey("BK-CATCHER-FOR-ASYNC-THROW-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1003")))
                .execute();

        ProcessInstance throwerInstance = testEngine.engine().startProcess()
                .byKey(throwerDefinition.key())
                .withBusinessKey("BK-THROWER-COMMIT-BEFORE-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "1003")))
                .execute();

        assertEquals(0, handlerInvocations.get(), "O throw não deveria ter disparado ainda — ficou pendente por causa do commitBefore.");
        testEngine.repository().assertThatProcessInstanceIsActive(throwerInstance.id());
        testEngine.repository().assertThatProcessInstanceIsActive(catcherInstance.id());

        List<ExecutableTask> pendingThrows = testEngine.repository().findExecutableTasksByProcessInstanceId(throwerInstance.id());
        assertEquals(1, pendingThrows.size());
        ExecutableTask throwTask = pendingThrows.get(0);
        assertEquals("THROW_ORDER_EVENT", throwTask.taskDefinitionId());
        assertEquals(ExecutableTaskType.EVENT_THROW, throwTask.type());

        ProcessInstance throwerAfterResuming = testEngine.engine().executeFromTask(throwTask);

        assertEquals(1, handlerInvocations.get(), "Retomar a ExecutableTask EVENT_THROW deveria disparar o throw de verdade.");
        assertEquals(ProcessInstanceStatus.COMPLETED, throwerAfterResuming.status());
        testEngine.repository().assertThatProcessInstanceIsCompleted(catcherInstance.id());
    }

    @Test
    @DisplayName("Quando ninguém está esperando a chave lançada, propaga TaskNotFoundException (política FAIL v1)")
    void throwingWithNoListenerPropagatesTaskNotFoundException() {
        TestEngine testEngine = SingletonsFactory.engine().build();
        ProcessDefinition throwerDefinition = testEngine.deploy("/processes/event-thrower-standalone.json");

        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().startProcess()
                        .byKey(throwerDefinition.key())
                        .withBusinessKey("BK-THROWER-NO-LISTENER-1")
                        .onTenant("tenant-a")
                        .withVariables(Map.of("orderId", new ProcessVariable("orderId", "NOBODY-WAITING")))
                        .execute());
    }
}
