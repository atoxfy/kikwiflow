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

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Documenta as validações de deploy adicionadas para os três nós que implementam
 * {@code CorrelationKeySource} — {@code EVENT_CATCHER}, {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT} e
 * {@code EVENT_THROWER} — fechando lacunas explicitamente documentadas como "não faz (ainda)" em
 * docs/engine/16-event-catcher-correlacao-de-eventos.md e docs/engine/17-boundary-interruptive-catch-event.md:
 * antes desta validação, um {@code providerBean} inexistente ou uma combinação de nó não suportada só falhava
 * em runtime, na primeira vez que o nó era alcançado.
 */
@DisplayName("Dado um processo com um nó de correlação de eventos mal configurado")
class CorrelationDeployValidationTest {

    private static RuntimeException deployAndCapture(TestEngine testEngine, String fixture) {
        return assertThrows(RuntimeException.class, () -> testEngine.deploy(fixture));
    }

    @Test
    @DisplayName("Deploy rejeita EVENT_CATCHER com catchType GROUP e providerType STATIC")
    void deployRejectsEventCatcherGroupWithStaticProvider() {
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/event-catcher-group-static-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Deploy rejeita EVENT_CATCHER com um BOUNDARY_INTERRUPTIVE_CATCH_EVENT anexado (só timers são suportados como boundary aqui)")
    void deployRejectsEventCatcherWithNonTimerBoundary() {
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/event-catcher-boundary-non-timer-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Deploy rejeita EVENT_CATCHER configurado como VARIABLE sem 'providerVariable'")
    void deployRejectsEventCatcherWithMissingProviderVariable() {
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/event-catcher-missing-provider-variable-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Deploy rejeita BOUNDARY_INTERRUPTIVE_CATCH_EVENT anexado a um EVENT_CATCHER (só EXTERNAL_TASK é suportado)")
    void deployRejectsInterruptiveCatchEventAttachedToEventCatcher() {
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/boundary-catch-event-attached-to-event-catcher-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Deploy rejeita EVENT_THROWER configurado como BEAN sem 'providerBean'")
    void deployRejectsEventThrowerWithMissingProviderBean() {
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/event-thrower-missing-provider-bean-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Deploy rejeita EVENT_THROWER configurado como BEAN com um bean não registrado")
    void deployRejectsEventThrowerWithUnresolvedProviderBean() {
        // Sem withCorrelationKeysProvider(...) registrado — mesmo padrão do teste de AnswerProvider BEAN
        // inexistente para ExclusiveGatewayDefinition.
        TestEngine testEngine = SingletonsFactory.engine().build();

        RuntimeException thrown = deployAndCapture(testEngine, "/processes/event-thrower-unresolved-provider-bean-invalid.json");

        assertInstanceOf(InvalidProcessDefinitionException.class, thrown.getCause());
    }
}
