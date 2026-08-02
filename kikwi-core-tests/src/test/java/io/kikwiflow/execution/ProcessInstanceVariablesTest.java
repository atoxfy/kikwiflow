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
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre o caminho externo de manipulação de variáveis de uma instância ativa —
 * {@code KikwiflowEngine.setVariables}/{@code unsetVariables}, o mesmo usado pela API REST
 * ({@code PUT}/{@code PUT .../unset} em {@code /process-instances/{id}/variables}) — em vez do caminho
 * interno via {@code ExecutionContext} dentro de um {@code TaskHandler}.
 */
@DisplayName("Dado um processo com uma instância ativa aguardando uma tarefa externa")
class ProcessInstanceVariablesTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;
    private final IdentityContext identityContext = new IdentityContext("test-actor", null);

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/external-task-wait.json");
    }

    @Test
    @DisplayName("Quando setVariables é chamado, então a variável passa a existir na instância")
    void setVariablesAddsTheVariableToTheInstance() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-SET-1")
                .execute();

        ProcessInstance updated = testEngine.engine().setVariables(
                instance.id(), Map.of("approved", new ProcessVariable("approved", true)), identityContext);

        assertTrue(updated.variables().containsKey("approved"));
        assertEquals(true, updated.variables().get("approved").value());
    }

    @Test
    @DisplayName("Quando unsetVariables é chamado, então a variável deixa de existir na instância")
    void unsetVariablesRemovesTheVariableFromTheInstance() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-UNSET-1")
                .withVariables(Map.of("temporaryFlag", new ProcessVariable("temporaryFlag", true)))
                .execute();

        assertTrue(instance.variables().containsKey("temporaryFlag"));

        ProcessInstance updated = testEngine.engine().unsetVariables(
                instance.id(), Set.of("temporaryFlag"), identityContext);

        assertFalse(updated.variables().containsKey("temporaryFlag"));
    }

    @Test
    @DisplayName("Quando unsetVariables remove uma entre várias variáveis, então as demais permanecem intactas")
    void unsetVariablesLeavesOtherVariablesUntouched() {
        ProcessInstance instance = testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey("BK-UNSET-2")
                .withVariables(Map.of(
                        "temporaryFlag", new ProcessVariable("temporaryFlag", true),
                        "customerId", new ProcessVariable("customerId", "CUST-1")
                ))
                .execute();

        ProcessInstance updated = testEngine.engine().unsetVariables(
                instance.id(), Set.of("temporaryFlag"), identityContext);

        assertFalse(updated.variables().containsKey("temporaryFlag"));
        assertEquals("CUST-1", updated.variables().get("customerId").value());
    }
}
