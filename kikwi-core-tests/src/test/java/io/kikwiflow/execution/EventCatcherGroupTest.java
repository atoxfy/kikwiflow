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
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o modo {@code catchType: GROUP} do nó {@code EVENT_CATCHER} (scatter-gather): N tarefas-filhas
 * geradas a partir de uma variável de lista, uma tarefa-mãe que rastreia a pendência, e conclusão de acordo
 * com {@code matchPolicy} (ALL/ANY).
 */
@DisplayName("Dado um processo com um EVENT_CATCHER em modo GROUP")
class EventCatcherGroupTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    private TestEngine testEngine;

    @Nested
    @DisplayName("Com matchPolicy ALL")
    class AllPolicy {

        private ProcessDefinition definition;

        @BeforeEach
        void setUp() {
            testEngine = SingletonsFactory.engine().build();
            definition = testEngine.deploy("/processes/event-catcher-group-all.json");
        }

        @Test
        @DisplayName("Ao iniciar, cria 1 tarefa-mãe e N tarefas-filhas a partir da lista")
        void createsParentAndChildTasksFromList() {
            ProcessInstance instance = startWithProducts(List.of("CARD_123", "LOAN_456"));

            testEngine.repository().assertHasPendingEventCatcherChildren(instance.id(), "WAIT_ALL_PRODUCTS", 2);

            List<ExternalTask> tasks = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
            long parents = tasks.stream().filter(t -> t.type() == ExternalTaskType.EVENT_CATCHER_PARENT).count();
            assertEquals(1, parents);
        }

        @Test
        @DisplayName("Correlacionar todas as chaves avança o fluxo e apaga mãe e filhas")
        void correlatingAllKeysAdvancesFlowAndCleansUpEverything() {
            ProcessInstance instance = startWithProducts(List.of("CARD_123", "LOAN_456"));

            ProcessInstance afterFirst = testEngine.engine().correlateMessage("PROD_CARD_123_ACTIVATED", Map.of(), IDENTITY);
            assertEquals(ProcessInstanceStatus.ACTIVE, afterFirst.status());
            testEngine.repository().assertHasPendingEventCatcherChildren(instance.id(), "WAIT_ALL_PRODUCTS", 1);
            // a filha correlacionada não é apagada imediatamente — fica CORRELATED até a mãe concluir, o que
            // preserva displayName/correlationKey para o Monitor sem precisar de nenhum campo de snapshot.
            testEngine.repository().assertHasCorrelatedEventCatcherChildren(instance.id(), "WAIT_ALL_PRODUCTS", 1);

            ProcessInstance afterSecond = testEngine.engine().correlateMessage("PROD_LOAN_456_ACTIVATED", Map.of(), IDENTITY);
            assertEquals(ProcessInstanceStatus.COMPLETED, afterSecond.status());
            testEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_ALL_PRODUCTS");
        }

        @Test
        @DisplayName("Correlacionar a mesma chave duas vezes lança TaskNotFoundException na segunda (idempotência via status CORRELATED)")
        void correlatingTheSameKeyTwiceInGroupModeThrowsOnSecondCall() {
            startWithProducts(List.of("CARD_123", "LOAN_456"));

            testEngine.engine().correlateMessage("PROD_CARD_123_ACTIVATED", Map.of(), IDENTITY);

            assertThrows(io.kikwiflow.exception.TaskNotFoundException.class, () ->
                    testEngine.engine().correlateMessage("PROD_CARD_123_ACTIVATED", Map.of(), IDENTITY));
        }

        @Test
        @DisplayName("Timeout após correlação parcial limpa tanto a filha já correlacionada quanto a pendente")
        void boundaryTimeoutCleansUpBothCorrelatedAndPendingChildren() {
            ProcessInstance instance = startWithProducts(List.of("CARD_123", "LOAN_456"));

            testEngine.engine().correlateMessage("PROD_CARD_123_ACTIVATED", Map.of(), IDENTITY);
            testEngine.repository().assertHasCorrelatedEventCatcherChildren(instance.id(), "WAIT_ALL_PRODUCTS", 1);

            List<ExecutableTask> executableTasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            ExecutableTask timeoutTask = executableTasks.stream()
                    .filter(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterTimeout = testEngine.engine().executeFromTask(timeoutTask);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());
            testEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_ALL_PRODUCTS");
        }

        @Test
        @DisplayName("Lista vazia falha rápido ao iniciar o processo, sem persistir nada")
        void emptyListFailsFastOnStart() {
            assertThrows(IllegalStateException.class, () -> testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-EMPTY")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("productIds", new ProcessVariable("productIds", List.<String>of())))
                    .execute());
        }

        @Test
        @DisplayName("Timeout do timer de borda interruptivo apaga a mãe e todas as filhas remanescentes")
        void boundaryTimeoutCleansUpParentAndAllChildren() {
            ProcessInstance instance = startWithProducts(List.of("CARD_123", "LOAN_456"));

            List<ExecutableTask> executableTasks = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            ExecutableTask timeoutTask = executableTasks.stream()
                    .filter(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterTimeout = testEngine.engine().executeFromTask(timeoutTask);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());
            testEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_ALL_PRODUCTS");
        }

        @Test
        @DisplayName("Concluir a política ALL normalmente remove o timer de borda interruptivo anexado, sem deixar ExecutableTask órfã")
        void completingAllPolicyNormallyRemovesTheAttachedInterruptiveTimer() {
            TestEngine followupEngine = SingletonsFactory.engine().build();
            ProcessDefinition followupDefinition = followupEngine.deploy("/processes/event-catcher-group-all-timeout-followup.json");

            ProcessInstance instance = followupEngine.engine().startProcess()
                    .byKey(followupDefinition.key())
                    .withBusinessKey("BK-GROUP-ALL-FOLLOWUP-1")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("productIds", new ProcessVariable("productIds", List.of("CARD_123", "LOAN_456"))))
                    .execute();

            followupEngine.engine().correlateMessage("PROD_CARD_123_ACTIVATED", Map.of(), IDENTITY);
            followupEngine.engine().correlateMessage("PROD_LOAN_456_ACTIVATED", Map.of(), IDENTITY);

            // O processo segue ativo (há uma EXTERNAL_TASK depois do catcher) — só assim dá pra provar que a
            // limpeza do timer de borda não é apenas efeito colateral do wipe em bloco de uma instância concluída.
            followupEngine.repository().assertThatProcessInstanceIsActive(instance.id());
            List<ExecutableTask> remainingExecutableTasks = followupEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            assertTrue(remainingExecutableTasks.stream().noneMatch(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId())),
                    "O timer de borda interruptivo anexado à mãe deveria ter sido removido após a política ALL ser satisfeita.");
        }

        private ProcessInstance startWithProducts(List<String> productIds) {
            return testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-ALL-" + productIds.hashCode())
                    .onTenant("tenant-a")
                    .withVariables(Map.of("productIds", new ProcessVariable("productIds", productIds)))
                    .execute();
        }
    }

    @Nested
    @DisplayName("Com matchPolicy ANY")
    class AnyPolicy {

        private ProcessDefinition definition;

        @BeforeEach
        void setUp() {
            testEngine = SingletonsFactory.engine().build();
            definition = testEngine.deploy("/processes/event-catcher-group-any.json");
        }

        @Test
        @DisplayName("A primeira correlação recebida avança o fluxo e remove as tarefas-irmãs remanescentes")
        void firstCorrelationWinsAndCleansUpSiblings() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-ANY-1")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("supplierIds", new ProcessVariable("supplierIds", List.of("A", "B", "C"))))
                    .execute();

            testEngine.repository().assertHasPendingEventCatcherChildren(instance.id(), "WAIT_FIRST_PROPOSAL", 3);

            ProcessInstance completed = testEngine.engine().correlateMessage("SUPPLIER_B_PROPOSAL", Map.of(), IDENTITY);

            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
            testEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_FIRST_PROPOSAL");
        }

        @Test
        @DisplayName("Correlacionar uma chave depois que outra já venceu a corrida lança TaskNotFoundException")
        void correlatingAfterAnotherKeyAlreadyWonThrows() {
            testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-ANY-2")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("supplierIds", new ProcessVariable("supplierIds", List.of("A", "B"))))
                    .execute();

            testEngine.engine().correlateMessage("SUPPLIER_A_PROPOSAL", Map.of(), IDENTITY);

            assertThrows(io.kikwiflow.exception.TaskNotFoundException.class, () ->
                    testEngine.engine().correlateMessage("SUPPLIER_B_PROPOSAL", Map.of(), IDENTITY));
        }

        @Test
        @DisplayName("Timeout do timer de borda interruptivo antes de qualquer correlação cancela a mãe e todas as filhas")
        void boundaryTimeoutBeforeAnyCorrelationCancelsParentAndAllChildren() {
            TestEngine timeoutEngine = SingletonsFactory.engine().build();
            ProcessDefinition timeoutDefinition = timeoutEngine.deploy("/processes/event-catcher-group-any-timeout.json");

            ProcessInstance instance = timeoutEngine.engine().startProcess()
                    .byKey(timeoutDefinition.key())
                    .withBusinessKey("BK-GROUP-ANY-TIMEOUT-1")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("supplierIds", new ProcessVariable("supplierIds", List.of("A", "B", "C"))))
                    .execute();

            timeoutEngine.repository().assertHasPendingEventCatcherChildren(instance.id(), "WAIT_FIRST_PROPOSAL", 3);

            List<ExecutableTask> executableTasks = timeoutEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            ExecutableTask timeoutTask = executableTasks.stream()
                    .filter(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId()))
                    .findFirst()
                    .orElseThrow();

            ProcessInstance afterTimeout = timeoutEngine.engine().executeFromTask(timeoutTask);

            assertEquals(ProcessInstanceStatus.COMPLETED, afterTimeout.status());
            timeoutEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_FIRST_PROPOSAL");
            assertThrows(io.kikwiflow.exception.TaskNotFoundException.class, () ->
                    timeoutEngine.engine().correlateMessage("SUPPLIER_A_PROPOSAL", Map.of(), IDENTITY));
        }

        @Test
        @DisplayName("Vencer a corrida (ANY) também remove o timer de borda interruptivo anexado à mãe")
        void winningTheRaceAlsoRemovesTheAttachedInterruptiveTimer() {
            TestEngine followupEngine = SingletonsFactory.engine().build();
            ProcessDefinition followupDefinition = followupEngine.deploy("/processes/event-catcher-group-any-timeout-followup.json");

            ProcessInstance instance = followupEngine.engine().startProcess()
                    .byKey(followupDefinition.key())
                    .withBusinessKey("BK-GROUP-ANY-FOLLOWUP-1")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("supplierIds", new ProcessVariable("supplierIds", List.of("A", "B", "C"))))
                    .execute();

            followupEngine.engine().correlateMessage("SUPPLIER_B_PROPOSAL", Map.of(), IDENTITY);

            // O processo segue ativo (há uma EXTERNAL_TASK depois do catcher) — só assim dá pra provar que a
            // limpeza do timer de borda não é apenas efeito colateral do wipe em bloco de uma instância concluída.
            followupEngine.repository().assertThatProcessInstanceIsActive(instance.id());
            List<ExecutableTask> remainingExecutableTasks = followupEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
            assertTrue(remainingExecutableTasks.stream().noneMatch(t -> "TIMER_SLA_TIMEOUT".equals(t.taskDefinitionId())),
                    "O timer de borda interruptivo anexado à mãe deveria ter sido removido após a política ANY ser satisfeita.");
        }
    }

    @Nested
    @DisplayName("Com providerType TEMPLATE (lista fixa de eventos declarada no .kikwi)")
    class TemplateProvider {

        private ProcessDefinition definition;

        @BeforeEach
        void setUp() {
            testEngine = SingletonsFactory.engine().build();
            definition = testEngine.deploy("/processes/event-catcher-group-template.json");
        }

        @Test
        @DisplayName("Correlacionar todas as chaves fixas do template avança o fluxo e limpa mãe e filhas")
        void correlatingAllTemplatedKeysAdvancesFlowAndCleansUpEverything() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-TEMPLATE-1")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("cpf", new ProcessVariable("cpf", "12345678900")))
                    .execute();

            testEngine.repository().assertHasPendingEventCatcherChildren(instance.id(), "WAIT_PRODUCTS_EFFECTIVATED", 2);

            ProcessInstance afterFirst = testEngine.engine().correlateMessage(
                    "CONTA_CORRENTE_12345678900_EFETIVADA", Map.of(), IDENTITY);
            assertEquals(ProcessInstanceStatus.ACTIVE, afterFirst.status());

            ProcessInstance afterSecond = testEngine.engine().correlateMessage(
                    "CARTAO_CREDITO_12345678900_EFETIVADA", Map.of(), IDENTITY);
            assertEquals(ProcessInstanceStatus.COMPLETED, afterSecond.status());
            testEngine.repository().assertEventCatcherResolved(instance.id(), "WAIT_PRODUCTS_EFFECTIVATED");
        }

        @Test
        @DisplayName("Correlacionar uma chave fora da lista fixa do template lança TaskNotFoundException")
        void correlatingAKeyOutsideTheFixedTemplateListThrows() {
            testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-GROUP-TEMPLATE-2")
                    .onTenant("tenant-a")
                    .withVariables(Map.of("cpf", new ProcessVariable("cpf", "99999999999")))
                    .execute();

            assertThrows(io.kikwiflow.exception.TaskNotFoundException.class, () ->
                    testEngine.engine().correlateMessage("EMPRESTIMO_99999999999_EFETIVADA", Map.of(), IDENTITY));
        }
    }
}
