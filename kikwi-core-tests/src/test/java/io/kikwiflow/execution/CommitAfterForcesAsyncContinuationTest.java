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
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta {@code commitAfter: true} como a segunda fronteira de continuação assíncrona (ver
 * docs/engine/06-execucao-sincrona-assincrona.md): ao contrário de {@code commitBefore} (que olha para o
 * PRÓXIMO nó), {@code commitAfter} é uma propriedade do nó que ACABOU de executar — {@code
 * ProcessExecutionManager.executeFlow} passa {@code Boolean.TRUE.equals(currentNode.commitAfter())} como {@code
 * forceAsync} para {@code Navigator.determineNextContinuation}, forçando a parada mesmo que o próximo nó não
 * declare {@code commitBefore}. TASK_A roda de verdade (efeito colateral já ocorreu, por isso o nome "commit
 * AFTER"), mas TASK_B só é criada como {@code ExecutableTask PENDING} — seu handler não roda na mesma chamada.
 */
@DisplayName("Dado um processo com uma EXECUTABLE_TASK com commitAfter=true seguida de uma sem commitBefore")
class CommitAfterForcesAsyncContinuationTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final AtomicBoolean taskARan = new AtomicBoolean(false);
    private final AtomicBoolean taskBRan = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withTaskHandler("taskAHandler", ctx -> taskARan.set(true))
                .withTaskHandler("taskBHandler", ctx -> taskBRan.set(true))
                .build();

        definition = testEngine.deploy("/processes/commit-after-forces-async.json");
    }

    @Test
    @DisplayName("Ao iniciar, TASK_A roda de verdade (commitAfter=true) mas TASK_B fica PENDING sem rodar inline")
    void taskARunsInlineButTaskBIsDeferredToAResumption() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CA-1")
                .execute();

        assertTrue(taskARan.get(), "TASK_A deveria ter rodado de verdade antes da parada forçada por commitAfter.");
        assertFalse(taskBRan.get(), "TASK_B não deveria ter rodado inline — commitAfter em TASK_A forçou a parada antes dela.");
        testEngine.repository().assertThatProcessInstanceIsActive(instance.id());

        List<ExecutableTask> pending = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id());
        assertEquals(1, pending.size());
        ExecutableTask taskB = pending.get(0);
        assertEquals("TASK_B", taskB.taskDefinitionId());
        assertEquals(ExecutableTaskStatus.PENDING, taskB.status());
    }

    @Test
    @DisplayName("Retomando TASK_B manualmente, seu handler roda e o processo conclui")
    void resumingTaskBRunsItsHandlerAndCompletesTheProcess() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-CA-2")
                .execute();

        ExecutableTask taskB = testEngine.repository().findExecutableTasksByProcessInstanceId(instance.id()).get(0);

        ProcessInstance completed = testEngine.engine().executeFromTask(taskB);

        assertTrue(taskBRan.get());
        assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
    }
}
