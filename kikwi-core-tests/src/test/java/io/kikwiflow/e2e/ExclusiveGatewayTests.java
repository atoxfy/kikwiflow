/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.e2e;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.assertion.AssertableKikwiEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.TestDecisionRuleResolver;
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.execution.api.JavaDelegate;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.enumerated.ProcessVariableVisibility;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.rule.api.DecisionRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ExclusiveGatewayTests {

    private KikwiflowEngine kikwiflowEngine;
    private AssertableKikwiEngine assertableKikwiEngine;
    private TestDelegateResolver delegateResolver;

    private JavaDelegate findPersonDataDelegate;
    private JavaDelegate doPaymentDelegate;
    private DecisionRule isPersonDataFilled;
    private TestDecisionRuleResolver decisionRuleResolver;

    @BeforeEach
    void setUp() {
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.delegateResolver = new TestDelegateResolver();
        this.decisionRuleResolver = new TestDecisionRuleResolver();

        findPersonDataDelegate = spy(new TestJavaDelegate(context -> {
            context.setVariable("step1", new ProcessVariable("step1", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));

        doPaymentDelegate = spy(new TestJavaDelegate(context -> {
            context.setVariable("step2", new ProcessVariable("step2", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));

        isPersonDataFilled = spy(new TestDecisionRule());
        decisionRuleResolver.register("isPersonDataFilled", isPersonDataFilled);
        delegateResolver.register("findPersonDataDelegate", findPersonDataDelegate);
        delegateResolver.register("doPaymentDelegate", doPaymentDelegate);

        KikwiflowConfig kikwiflowConfig = new KikwiflowConfig();
        ProcessDefinitionService processDefinitionService = SingletonsFactory.processDefinitionService(SingletonsFactory.bpmnParser(), assertableKikwiEngine,  SingletonsFactory.deployValidator(delegateResolver, decisionRuleResolver));
        Navigator navigator = SingletonsFactory.navigator(decisionRuleResolver);
        ProcessExecutionManager processExecutionManager = SingletonsFactory.processExecutionManager(delegateResolver, navigator, kikwiflowConfig);
        List<ExecutionEventListener> executionEventListeners = null;
        this.kikwiflowEngine = new KikwiflowEngine(processDefinitionService, navigator, processExecutionManager, assertableKikwiEngine, kikwiflowConfig, executionEventListeners);
    }

    @AfterEach
    public void resetEngine() {
        assertableKikwiEngine.reset();
    }

    @Test
    @DisplayName("Deve executar um processo com gateway exclusivo (XOR)")
    void shouldExecuteProcessWithXORGateway() throws Exception {
        // Arrange: Deploy do processo
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("gateway-process-test.bpmn");
        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);

        // Act: Inicia o processo. A execução síncrona deve parar após a Task_Commit_After.
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
            .byKey(processDefinition.key())
            .withBusinessKey(UUID.randomUUID().toString())
            .execute();

        // Assert: Fase 1 - Após o start
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status(), "O processo deve estar ativo.");
        verify(findPersonDataDelegate, times(1)).execute(any());
        verify(doPaymentDelegate, times(0)).execute(any()); // Ainda não foi executado

        assertTrue(processInstance.variables().containsKey("step1"), "A variável da step 1 deve existir.");
        assertFalse(processInstance.variables().containsKey("step2"), "A variável da step 2 NÃO deve existir.");

        // Verifica se um job foi criado para a próxima tarefa (doPaymentTask)
        var optTask = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(optTask.isPresent(), "Deveria existir um job pendente para a doPaymentTask.");
        var task = optTask.get();
        assertEquals("doPaymentTask", task.taskDefinitionId());

        // Act: Simula o worker executando o primeiro job
        processInstance = kikwiflowEngine.executeFromTask(task);

        // Assert: Fase 3 - Finalização
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status(), "O processo deveria estar completo.");
        assertNotNull(processInstance.endedAt(), "O processo deveria ter uma data de término.");
        verify(doPaymentDelegate, times(1)).execute(any()); // Agora foi executado

        assertTrue(processInstance.variables().containsKey("step2"), "A variável da step 4 deve existir.");

        // Garante que não há mais jobs pendentes para esta instância
        assertFalse(assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id()).isPresent(), "Não deveria haver mais jobs pendentes.");
    }

    private static class TestDecisionRule implements DecisionRule {

        @Override
        public boolean evaluate(Map<String, ProcessVariable> variables) {
            return true;
        }
    }

    /**
     * A concrete, test-friendly implementation of JavaDelegate.
     * This avoids Mockito's issues with spying on lambda expressions.
     */
    private static class TestJavaDelegate implements JavaDelegate {
        private final java.util.function.Consumer<io.kikwiflow.execution.api.ExecutionContext> logic;

        public TestJavaDelegate(java.util.function.Consumer<io.kikwiflow.execution.api.ExecutionContext> logic) {
            this.logic = logic;
        }

        @Override
        public void execute(io.kikwiflow.execution.api.ExecutionContext execution) {
            if (logic != null) {
                logic.accept(execution);
            }
        }
    }
}