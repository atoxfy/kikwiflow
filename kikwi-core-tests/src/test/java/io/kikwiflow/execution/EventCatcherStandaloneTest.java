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
import io.kikwiflow.execution.api.dto.CorrelationItem;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o modo {@code catchType: STANDALONE} do nó {@code EVENT_CATCHER}: uma única tarefa reativa
 * destravada por chave de correlação de negócio (não por {@code taskId}) via
 * {@code KikwiflowEngine.correlateMessage(...)}.
 */
@DisplayName("Dado um processo com um EVENT_CATCHER standalone aguardando correlação")
class EventCatcherStandaloneTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/event-catcher-standalone.json");
    }

    @Test
    @DisplayName("Quando o processo inicia, a chave de correlação é resolvida da variável e uma ExternalTask reativa fica ativa")
    void resolvesCorrelationKeyFromVariableAndCreatesReactiveTask() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-STANDALONE-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

        List<ExternalTask> tasks = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        assertEquals(1, tasks.size());
        ExternalTask task = tasks.get(0);
        assertEquals("ORDER_9988_PAID", task.correlationKey());
        assertEquals(ExternalTaskType.EVENT_CATCHER_STANDALONE, task.type());
    }

    @Test
    @DisplayName("Quando correlateMessage é chamado com a chave certa, o processo avança e a tarefa é removida")
    void correlatingWithTheRightKeyAdvancesTheProcess() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-STANDALONE-2")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        ProcessInstance completed = testEngine.engine().correlateMessage(
                "ORDER_9988_PAID",
                Map.of("paymentStatus", new ProcessVariable("paymentStatus", "CONFIRMED")),
                IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        testEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "WAIT_ORDER_PAID");
    }

    @Test
    @DisplayName("Quando correlateMessage é chamado com uma chave desconhecida, lança TaskNotFoundException")
    void correlatingWithUnknownKeyThrows() {
        testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-STANDALONE-3")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("ORDER_UNKNOWN_PAID", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Quando correlateMessage é repetido para uma chave já consumida, lança TaskNotFoundException (idempotência)")
    void correlatingTheSameKeyTwiceThrowsOnTheSecondCall() {
        testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-STANDALONE-4")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        testEngine.engine().correlateMessage("ORDER_9988_PAID", Map.of(), IDENTITY);

        assertThrows(TaskNotFoundException.class, () ->
                testEngine.engine().correlateMessage("ORDER_9988_PAID", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("providerType TEMPLATE encadeia texto fixo e variável na chave e no rótulo (\"event builder\")")
    void templateProviderChainsLiteralAndVariableSegments() {
        TestEngine templateEngine = SingletonsFactory.engine().build();
        ProcessDefinition templateDefinition = templateEngine.deploy("/processes/event-catcher-standalone-template.json");

        ProcessInstance instance = templateEngine.engine().startProcess()
                .byKey(templateDefinition.key())
                .withBusinessKey("BK-STANDALONE-TEMPLATE-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("cpf", new ProcessVariable("cpf", "12345678900")))
                .execute();

        List<ExternalTask> tasks = templateEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        assertEquals(1, tasks.size());
        assertEquals("CONTA_CORRENTE_12345678900_EFETIVADA", tasks.get(0).correlationKey());
        assertEquals("Efetivacao da Conta Corrente", tasks.get(0).displayName());

        ProcessInstance completed = templateEngine.engine().correlateMessage(
                "CONTA_CORRENTE_12345678900_EFETIVADA", Map.of(), IDENTITY);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }

    @Test
    @DisplayName("Timeout do timer de borda interruptivo cancela a espera e a chave antiga não pode mais ser correlacionada")
    void boundaryTimeoutCancelsTheWaitAndOldKeyCannotBeCorrelatedAnymore() {
        TestEngine timeoutEngine = SingletonsFactory.engine().build();
        ProcessDefinition timeoutDefinition = timeoutEngine.deploy("/processes/event-catcher-standalone-timeout.json");

        ProcessInstance instance = timeoutEngine.engine().startProcess()
                .byKey(timeoutDefinition.key())
                .withBusinessKey("BK-STANDALONE-TIMEOUT-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        List<ExecutableTask> executableTasks = timeoutEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        ExecutableTask timeoutTask = executableTasks.stream()
                .filter(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId()))
                .findFirst()
                .orElseThrow();

        ProcessInstance afterTimeout = timeoutEngine.engine().executeFromTask(timeoutTask);

        assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());
        timeoutEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "WAIT_ORDER_PAID");
        assertThrows(TaskNotFoundException.class, () ->
                timeoutEngine.engine().correlateMessage("ORDER_9988_PAID", Map.of(), IDENTITY));
    }

    @Test
    @DisplayName("Correlação normal remove o timer de borda interruptivo anexado, sem deixar ExecutableTask órfã")
    void normalCorrelationRemovesTheAttachedInterruptiveTimer() {
        TestEngine followupEngine = SingletonsFactory.engine().build();
        ProcessDefinition followupDefinition = followupEngine.deploy("/processes/event-catcher-standalone-timeout-followup.json");

        ProcessInstance instance = followupEngine.engine().startProcess()
                .byKey(followupDefinition.key())
                .withBusinessKey("BK-STANDALONE-FOLLOWUP-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "9988")))
                .execute();

        followupEngine.engine().correlateMessage("ORDER_9988_PAID", Map.of(), IDENTITY);

        // O processo segue ativo (há uma EXTERNAL_TASK depois do catcher) — só assim dá pra provar que a
        // limpeza do timer de borda não é apenas efeito colateral do wipe em bloco de uma instância concluída.
        followupEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        List<ExecutableTask> remainingExecutableTasks = followupEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertTrue(remainingExecutableTasks.stream().noneMatch(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId())),
                "O timer de borda interruptivo anexado ao EVENT_CATCHER deveria ter sido removido após a correlação normal.");
    }

    @Test
    @DisplayName("providerType BEAN delega a resolução da chave para um CorrelationKeysProvider registrado")
    void beanProviderResolvesCorrelationKeyThroughRegisteredProvider() {
        TestEngine beanEngine = SingletonsFactory.engine()
                .withCorrelationKeysProvider("orderPaymentCorrelationProvider", context -> {
                    Object orderId = context.getVariableValue("orderId").orElseThrow();
                    return List.of(new CorrelationItem("ORDER_" + orderId + "_PAID", "Pagamento do pedido " + orderId));
                })
                .build();
        ProcessDefinition beanDefinition = beanEngine.deploy("/processes/event-catcher-standalone-bean.json");

        ProcessInstance instance = beanEngine.engine().startProcess()
                .byKey(beanDefinition.key())
                .withBusinessKey("BK-STANDALONE-BEAN-1")
                .onTenant("tenant-a")
                .withVariables(Map.of("orderId", new ProcessVariable("orderId", "4321")))
                .execute();

        List<ExternalTask> tasks = beanEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        assertEquals(1, tasks.size());
        assertEquals("ORDER_4321_PAID", tasks.get(0).correlationKey());
        assertEquals("Pagamento do pedido 4321", tasks.get(0).displayName());

        ProcessInstance completed = beanEngine.engine().correlateMessage("ORDER_4321_PAID", Map.of(), IDENTITY);
        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }

    @Test
    @DisplayName("providerType STATIC resolve a mesma chave para instâncias diferentes, isoladas por tenant em correlateMessage")
    void staticProviderKeysAreIsolatedByTenant() {
        TestEngine staticEngine = SingletonsFactory.engine().build();
        ProcessDefinition staticDefinition = staticEngine.deploy("/processes/event-catcher-standalone-static.json");
        IdentityContext tenantAIdentity = new IdentityContext("test-actor", "tenant-a");
        IdentityContext tenantBIdentity = new IdentityContext("test-actor", "tenant-b");
        IdentityContext tenantCIdentity = new IdentityContext("test-actor", "tenant-c");

        ProcessInstance instanceTenantA = staticEngine.engine().startProcess()
                .byKey(staticDefinition.key())
                .withBusinessKey("BK-STATIC-TENANT-A")
                .onTenant("tenant-a")
                .execute();
        ProcessInstance instanceTenantB = staticEngine.engine().startProcess()
                .byKey(staticDefinition.key())
                .withBusinessKey("BK-STATIC-TENANT-B")
                .onTenant("tenant-b")
                .execute();

        staticEngine.repository().assertThatProcessInstanceIsActive(instanceTenantA.id());

        // Um tenant sem nenhuma instância aguardando essa chave não enxerga nada, mesmo a chave técnica batendo.
        assertThrows(TaskNotFoundException.class, () ->
                staticEngine.engine().correlateMessage("GLOBAL_SIGNAL_RECEIVED", Map.of(), tenantCIdentity));

        // Resolver a chave do tenant A não deve completar (nem afetar) a espera equivalente do tenant B.
        ProcessInstance completedTenantA = staticEngine.engine().correlateMessage(
                "GLOBAL_SIGNAL_RECEIVED", Map.of(), tenantAIdentity);
        assertEquals(ProcessInstanceStatus.COMPLETED, completedTenantA.status());
        staticEngine.repository().assertThatProcessInstanceIsActive(instanceTenantB.id());
        staticEngine.repository().assertHasActiveExternalTaskOn(instanceTenantB.id(), "WAIT_GLOBAL_SIGNAL");

        ProcessInstance completedTenantB = staticEngine.engine().correlateMessage(
                "GLOBAL_SIGNAL_RECEIVED", Map.of(), tenantBIdentity);
        assertEquals(ProcessInstanceStatus.COMPLETED, completedTenantB.status());
    }
}
