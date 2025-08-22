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
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Testes de ponta a ponta para fluxos de processo que envolvem tarefas humanas sequenciais.
 * O objetivo principal é validar que a engine pausa corretamente nos "wait states" (estados de espera)
 * e cria as tarefas externas correspondentes.
 */
public class LinearHumanTasksTests {

    private final KikwiflowEngine kikwiflowEngine;
    private final AssertableKikwiEngine assertableKikwiEngine;
    private final TestDelegateResolver delegateResolver;
    private final KikwiflowConfig kikwiflowConfig;

    private KikwiflowConfig getAndInitConfig(){
        KikwiflowConfig kikwiflowConfig1 = new KikwiflowConfig();
        kikwiflowConfig1.statsEnabled();
        kikwiflowConfig1.outboxEventsEnabled();
        return kikwiflowConfig1;
    }

    public LinearHumanTasksTests(){
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.kikwiflowConfig = getAndInitConfig();
        this.delegateResolver = new TestDelegateResolver();
        this.kikwiflowEngine = new KikwiflowEngine(assertableKikwiEngine, kikwiflowConfig, delegateResolver, Collections.emptyList());
    }


    @AfterEach
    public void resetEngine(){
        assertableKikwiEngine.reset();
    }


    @Test
    @DisplayName("Deve iniciar um processo e pausar na primeira tarefa humana")
    void shouldStartAndPauseAtFirstHumanTask() throws Exception {
        //arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sequential-human-tasks.bpmn");

        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);
        String processDefinitionKey = processDefinition.key();
        String businessKey = "anyBusinessKey";
        Map<String, Object> startVariables = new HashMap<>();
        String initialVar = UUID.randomUUID().toString();
        String initialVarKey = "myVar";
        startVariables.put(initialVarKey, initialVar);

        //act
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
                .byKey(processDefinitionKey)
                .withBusinessKey(businessKey)
                .withVariables(startVariables)
                .execute();

        //assert
        assertNotNull(processInstance.id());
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status(), "Process should be active and waiting.");
        assertableKikwiEngine.assertThatProcessInstanceIsActive(processInstance.id());
        assertableKikwiEngine.assertHasActiveExternalTaskOn(processInstance.id(), "external-task-1");
    }

    @Test
    @DisplayName("Deve percorrer todo o fluxo de tarefas humanas sequenciais com sucesso")
    void shouldRunThroughTheEntireSequentialHumanTaskProcess() throws Exception {
        // Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sequential-human-tasks.bpmn");
        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);
        ProcessInstance processInstance = kikwiflowEngine.startProcess()
            .byKey(processDefinition.key())
            .withBusinessKey("e2e-sequential-flow")
            .execute();

        // Assert
        assertNotNull(processInstance.id());
        assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status());
        assertableKikwiEngine.assertHasActiveExternalTaskOn(processInstance.id(), "external-task-1");

        // Act & Assert
        for (int i = 1; i <= 5; i++) {
            String currentTaskDefinitionId = "external-task-" + i;

            // Find the current active task
            ExternalTask taskToComplete = assertableKikwiEngine.findExternalTasksByProcessInstanceId(processInstance.id())
                .stream()
                .filter(task -> task.taskDefinitionId().equals(currentTaskDefinitionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tarefa não encontrada: " + currentTaskDefinitionId));

            // Complete the task
            Map<String, Object> completionVariables = Map.of("task" + i + "_completed", true);
            processInstance = kikwiflowEngine.completeExternalTask(taskToComplete.id(), completionVariables);

            // Assert the state after each completion
            if (i < 5) {
                assertEquals(ProcessInstanceStatus.ACTIVE, processInstance.status(), "Processo deveria continuar ativo após a tarefa " + i);
                String nextTaskDefinitionId = "external-task-" + (i + 1);
                assertableKikwiEngine.assertHasActiveExternalTaskOn(processInstance.id(), nextTaskDefinitionId);
            }
        }

        // Final Assert: The process should now be completed
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status(), "Processo deveria estar completo após a última tarefa.");
        assertNotNull(processInstance.endedAt(), "Processo deveria ter uma data de término.");
        assertTrue(assertableKikwiEngine.findExternalTasksByProcessInstanceId(processInstance.id()).isEmpty(), "Não deveriam existir mais tarefas externas ativas.");
        assertableKikwiEngine.assertThatProcessInstanceIsCompleted(processInstance.id());
    }
}
