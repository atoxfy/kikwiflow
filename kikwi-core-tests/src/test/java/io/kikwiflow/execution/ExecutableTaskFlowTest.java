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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o caminho 100% síncrono do motor: um processo sem nenhum nó {@code commitBefore}/{@code commitAfter}
 * roda do início ao fim dentro de uma única chamada de {@code ProcessStarter.execute()}, sem nenhum passo
 * assíncrono ou intervenção do {@code TaskAcquirer}.
 */
@DisplayName("Dado um processo com uma única tarefa executável síncrona")
class ExecutableTaskFlowTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final AtomicBoolean taskHandlerRan = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withTaskHandler("calculateRisk", ctx -> {
                    taskHandlerRan.set(true);
                    ctx.setVariable("riskScore", new ProcessVariable("riskScore", 87.5));
                })
                .build();

        definition = testEngine.deploy("/processes/executable-task-flow.json");
    }

    @Test
    @DisplayName("Quando o processo é iniciado, então ele conclui na mesma chamada e a tarefa executável roda")
    void completesSynchronouslyInASingleCallAndRunsTheTaskHandler() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-001")
                .execute();

        assertTrue(taskHandlerRan.get(), "O TaskHandler 'calculateRisk' deveria ter sido executado.");
        assertEquals(ProcessInstanceStatus.COMPLETED, instance.status());
    }

    @Test
    @DisplayName("Quando o processo conclui de forma síncrona, então ele nunca chega a ser persistido no repositório")
    void neverPersistsAPurelySynchronousInstance() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-002")
                .execute();

        // Uma instância que começa e termina na mesma chamada síncrona nunca cruza uma fronteira transacional —
        // o motor nem chega a criar o registro no repositório (ver ContinuationService.handleContinuation).
        testEngine.repository().assertThatProcessInstanceNotExistsInRuntimeContext(instance.id());
    }
}
