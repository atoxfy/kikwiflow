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
import io.kikwiflow.execution.DecisionRuleResolver;
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
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class InterruptiveTimerTests {

    private KikwiflowEngine kikwiflowEngine;
    private AssertableKikwiEngine assertableKikwiEngine;
    private TestDelegateResolver delegateResolver;
    private JavaDelegate sendToRecovery;
    private TestDecisionRuleResolver decisionRuleResolver;

    @BeforeEach
    void setUp() {
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.delegateResolver = new TestDelegateResolver();
        this.decisionRuleResolver = new TestDecisionRuleResolver();

        sendToRecovery = spy(new TestJavaDelegate(context -> {
            context.setVariable("step1", new ProcessVariable("step1", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));

        delegateResolver.register("sendToRecovery", sendToRecovery);
        KikwiflowConfig kikwiflowConfig = new KikwiflowConfig();
        DecisionRuleResolver decisionRuleResolver = new TestDecisionRuleResolver();
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
    @DisplayName("Deve executar um processo com timer interruptivo")
    void shouldExecuteProcessWithInteruptiveTimer() throws Exception {
        // Arrange: Deploy do processo
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("interruptive-timer.bpmn");
        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);

        // Act: Inicia o processo. A execução síncrona deve parar após a Task_Commit_After.
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
            .byKey(processDefinition.key())
            .withBusinessKey(UUID.randomUUID().toString())
            .execute();

        assertNotNull(processInstance.id());
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status());
        assertableKikwiEngine.assertHasActiveExternalTaskOn(processInstance.id(), "doContactTask");
        verify(sendToRecovery, times(0)).execute(any()); // Ainda não foi executado

        Optional<ExecutableTask> executableTask = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(executableTask.isPresent(), "Deveria existir um job pendente para a sendToRecoveryTask.");
        var task = executableTask.get();
        assertEquals("interruptiveTimerEvent", task.taskDefinitionId());

        processInstance = kikwiflowEngine.executeFromTask(task);


        Optional<ExecutableTask> sendToRecoveryTaskOpt = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(sendToRecoveryTaskOpt.isPresent(), "Deveria existir um job pendente para a sendToRecoveryTask.");
        var sendToRecoveryTask = sendToRecoveryTaskOpt.get();
        assertEquals("sendToRecoveryTask", sendToRecoveryTask.taskDefinitionId());
        verify(sendToRecovery, times(0)).execute(any()); // Ainda não foi executado


        processInstance = kikwiflowEngine.executeFromTask(sendToRecoveryTask);

        // Assert: Fase 3 - Finalização
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status(), "O processo deveria estar completo.");
        assertNotNull(processInstance.endedAt(), "O processo deveria ter uma data de término.");
        verify(sendToRecovery, times(1)).execute(any()); // Agora foi executado

        assertTrue(processInstance.variables().containsKey("step1"), "A variável da step 4 deve existir.");

        // Garante que não há mais jobs pendentes para esta instância
        assertFalse(assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id()).isPresent(), "Não deveria haver mais jobs pendentes.");
        assertableKikwiEngine.assertHasntActiveExternalTaskOn(processInstance.id(), "doContactTask");

    }


    @Test
    @DisplayName("Cenário 2: Deve cancelar o timer quando a tarefa principal é concluida")
    void shouldCancelTimerWhenMainTaskIsCompleted() throws Exception {
        // Arrange: Deploy do processo
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("interruptive-timer.bpmn");
        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);

        // Act: Inicia o processo. A execução síncrona deve parar após a Task_Commit_After.
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
                .byKey(processDefinition.key())
                .withBusinessKey(UUID.randomUUID().toString())
                .execute();

        assertNotNull(processInstance.id());
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status());
        assertableKikwiEngine.assertHasActiveExternalTaskOn(processInstance.id(), "doContactTask");
        verify(sendToRecovery, times(0)).execute(any()); // Ainda não foi executado

        Optional<ExecutableTask> executableTask = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(executableTask.isPresent(), "Deveria existir um job pendente para a sendToRecoveryTask.");
        var task = executableTask.get();
        assertEquals("interruptiveTimerEvent", task.taskDefinitionId());

        ExternalTask taskToComplete = assertableKikwiEngine.findExternalTasksByProcessInstanceId(processInstance.id())
                .stream()
                .filter(et -> et.taskDefinitionId().equals("doContactTask"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tarefa não encontrada: doContactTask"));


        ProcessVariable processVariable = new ProcessVariable("step1", ProcessVariableVisibility.PUBLIC, null, true);
        Map<String, ProcessVariable> completionVariables = Map.of(processVariable.name(), processVariable);
        processInstance = kikwiflowEngine.completeExternalTask(taskToComplete.id(), null, completionVariables);
        // Assert: Fase 3 - Finalização
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status(), "O processo deveria estar completo.");
        assertNotNull(processInstance.endedAt(), "O processo deveria ter uma data de término.");
        verify(sendToRecovery, times(0)).execute(any()); // Agora foi executado

        assertTrue(processInstance.variables().containsKey("step1"), "A variável da step 4 deve existir.");

        // Garante que não há mais jobs pendentes para esta instância
        assertFalse(assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id()).isPresent(), "Não deveria haver mais jobs pendentes.");
        assertableKikwiEngine.assertHasntActiveExternalTaskOn(processInstance.id(), "doContactTask");
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