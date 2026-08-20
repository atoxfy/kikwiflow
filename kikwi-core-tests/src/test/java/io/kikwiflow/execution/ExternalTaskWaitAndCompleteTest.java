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
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Documenta o mecanismo assíncrono mais básico do motor: um nó {@code EXTERNAL_TASK} pausa a execução até uma
 * chamada explícita de {@code completeExternalTask}, dirigida manualmente aqui em vez de depender de um
 * worker/poller em background — o que mantém o teste determinístico.
 */
@DisplayName("Dado um processo com uma tarefa externa aguardando conclusão")
class ExternalTaskWaitAndCompleteTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/external-task-wait.json");
    }

    @Test
    @DisplayName("Quando o processo é iniciado, então uma tarefa externa fica ativa e a instância permanece ativa")
    void createsAnActiveExternalTaskAndKeepsTheInstanceActive() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-WAIT-1")
                .execute();

        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
        testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "WAIT_FOR_INPUT");
    }

    @Test
    @DisplayName("Quando a tarefa externa é completada, então o processo conclui e a tarefa deixa de estar ativa")
    void completingTheExternalTaskFinishesTheProcess() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-WAIT-2")
                .execute();

        List<ExternalTask> pendingTasks = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        assertEquals(1, pendingTasks.size());
        String externalTaskId = pendingTasks.get(0).id();

        IdentityContext identityContext = new IdentityContext("test-actor", null);
        Map<String, ProcessVariable> variables = Map.of("inputReceived", new ProcessVariable("inputReceived", true));

        ProcessInstance completed = testEngine.engine().completeExternalTask(externalTaskId, variables, identityContext);

        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        testEngine.repository().assertHasntActiveExternalTaskOn(instance.id(), "WAIT_FOR_INPUT");
    }
}
