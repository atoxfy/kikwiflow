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
import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.execution.*;
import io.kikwiflow.execution.api.JavaDelegate;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.enumerated.ProcessVariableVisibility;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AsynchronousContinuationsTests {

    private KikwiflowEngine kikwiflowEngine;
    private AssertableKikwiEngine assertableKikwiEngine;
    private TestDelegateResolver delegateResolver;

    private JavaDelegate delegate1;
    private JavaDelegate delegate2;
    private JavaDelegate delegate3;
    private JavaDelegate delegate4;




    @BeforeEach
    void setUp() {
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.delegateResolver = new TestDelegateResolver();

        // Criando spies para os delegates para que possamos verificar suas chamadas
        // Usamos uma classe concreta para evitar problemas do Mockito com lambdas.
        delegate1 = spy(new TestJavaDelegate(context -> {
            context.setVariable("step1", new ProcessVariable("step1", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));
        delegate2 = spy(new TestJavaDelegate(context -> {
            context.setVariable("step2", new ProcessVariable("step2", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));
        delegate3 = spy(new TestJavaDelegate(context -> {
            context.setVariable("step3", new ProcessVariable("step3", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));
        delegate4 = spy(new TestJavaDelegate(context -> {
            context.setVariable("step4", new ProcessVariable("step4", ProcessVariableVisibility.PUBLIC, null, "done"));
        }));

        delegateResolver.register("delegate1", delegate1);
        delegateResolver.register("delegate2", delegate2);
        delegateResolver.register("delegate3", delegate3);
        delegateResolver.register("delegate4", delegate4);

        KikwiflowConfig kikwiflowConfig = new KikwiflowConfig();
        DecisionRuleResolver decisionRuleResolver = new TestDecisionRuleResolver();
        ProcessDefinitionService processDefinitionService = SingletonsFactory.processDefinitionService(SingletonsFactory.bpmnParser(), assertableKikwiEngine);
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
    @DisplayName("Deve executar um processo com limites de transação (commit-before/after) corretamente")
    void shouldExecuteProcessWithCommitBeforeAndAfter() throws Exception {
        // Arrange: Deploy do processo
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("async-continuations-process.bpmn");
        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);

        // Act: Inicia o processo. A execução síncrona deve parar após a Task_Commit_After.
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
            .byKey(processDefinition.key()).withBusinessKey(UUID.randomUUID().toString())
            .execute();

        // Assert: Fase 1 - Após o start
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status(), "O processo deve estar ativo.");
        verify(delegate1, times(1)).execute(any());
        verify(delegate2, times(1)).execute(any());
        verify(delegate3, times(0)).execute(any()); // Ainda não foi executado
        verify(delegate4, times(0)).execute(any()); // Ainda não foi executado

        assertTrue(processInstance.variables().containsKey("step1"), "A variável da step 1 deve existir.");
        assertTrue(processInstance.variables().containsKey("step2"), "A variável da step 2 deve existir.");

        // Verifica se um job foi criado para a próxima tarefa (Task_Async_3)
        var optTask = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(optTask.isPresent(), "Deveria existir um job pendente para a Task_Async_3.");
        var task = optTask.get();
        assertEquals("Task_Async_3", task.taskDefinitionId());

        // Act: Simula o worker executando o primeiro job
        processInstance = kikwiflowEngine.executeFromTask(task);

        // Assert: Fase 2 - Após o primeiro job
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status(), "O processo ainda deve estar ativo.");
        verify(delegate3, times(1)).execute(any()); // Agora foi executado
        verify(delegate4, times(0)).execute(any()); // Ainda não

        assertTrue(processInstance.variables().containsKey("step3"), "A variável da step 3 deve existir.");

        // Verifica se um NOVO job foi criado para a Task_Commit_Before
        var job2 = assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id());
        assertTrue(job2.isPresent(), "Deveria existir um novo job pendente para a Task_Commit_Before.");
        assertEquals("Task_Commit_Before", job2.get().taskDefinitionId());

        // Act: Simula o worker executando o segundo job
        processInstance = kikwiflowEngine.executeFromTask(job2.get());

        // Assert: Fase 3 - Finalização
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status(), "O processo deveria estar completo.");
        assertNotNull(processInstance.endedAt(), "O processo deveria ter uma data de término.");
        verify(delegate4, times(1)).execute(any()); // Agora foi executado

        assertTrue(processInstance.variables().containsKey("step4"), "A variável da step 4 deve existir.");

        // Garante que não há mais jobs pendentes para esta instância
        assertFalse(assertableKikwiEngine.findAndGetFirstPendingExecutableTask(processInstance.id()).isPresent(), "Não deveria haver mais jobs pendentes.");
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