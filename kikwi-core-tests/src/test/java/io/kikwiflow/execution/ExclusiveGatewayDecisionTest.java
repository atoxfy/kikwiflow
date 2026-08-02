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
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o roteamento de um {@code EXCLUSIVE_GATEWAY} com {@code providerType: BEAN} — a resposta do
 * {@code AnswerProvider} decide qual das duas tarefas executáveis subsequentes roda, provando que o motor
 * segue o ramo correto e não o outro.
 */
@DisplayName("Dado um processo com um gateway exclusivo decidido por um AnswerProvider (BEAN)")
class ExclusiveGatewayDecisionTest {

    @Nested
    @DisplayName("Quando o AnswerProvider resolve 'APROVADO'")
    class WhenTheAnswerProviderResolvesApproved {

        private final AtomicBoolean approvedTaskRan = new AtomicBoolean(false);
        private final AtomicBoolean fraudTaskRan = new AtomicBoolean(false);
        private TestEngine testEngine;
        private ProcessDefinition definition;

        @BeforeEach
        void setUp() {
            testEngine = SingletonsFactory.engine()
                    .withTaskHandler("calculateRisk", ctx -> {})
                    .withTaskHandler("notifyApproved", ctx -> approvedTaskRan.set(true))
                    .withTaskHandler("notifyFraud", ctx -> fraudTaskRan.set(true))
                    .withAnswerProvider("riskStrategy", ctx -> "APROVADO")
                    .build();
            definition = testEngine.deploy("/processes/exclusive-gateway-decision.json");
        }

        @Test
        @DisplayName("Então o motor segue o ramo de aprovação, e apenas ele")
        void followsOnlyTheApprovedBranch() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-APROVADO")
                    .execute();

            assertTrue(approvedTaskRan.get(), "A tarefa 'notifyApproved' deveria ter rodado.");
            assertFalse(fraudTaskRan.get(), "A tarefa 'notifyFraud' NÃO deveria ter rodado.");
            assertEquals(ProcessInstanceStatus.COMPLETED, instance.status());
        }
    }

    @Nested
    @DisplayName("Quando o AnswerProvider resolve 'FRAUDE'")
    class WhenTheAnswerProviderResolvesFraud {

        private final AtomicBoolean approvedTaskRan = new AtomicBoolean(false);
        private final AtomicBoolean fraudTaskRan = new AtomicBoolean(false);
        private TestEngine testEngine;
        private ProcessDefinition definition;

        @BeforeEach
        void setUp() {
            testEngine = SingletonsFactory.engine()
                    .withTaskHandler("calculateRisk", ctx -> {})
                    .withTaskHandler("notifyApproved", ctx -> approvedTaskRan.set(true))
                    .withTaskHandler("notifyFraud", ctx -> fraudTaskRan.set(true))
                    .withAnswerProvider("riskStrategy", ctx -> "FRAUDE")
                    .build();
            definition = testEngine.deploy("/processes/exclusive-gateway-decision.json");
        }

        @Test
        @DisplayName("Então o motor segue o ramo de suspeita de fraude, e apenas ele")
        void followsOnlyTheFraudBranch() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-FRAUDE")
                    .execute();

            assertTrue(fraudTaskRan.get(), "A tarefa 'notifyFraud' deveria ter rodado.");
            assertFalse(approvedTaskRan.get(), "A tarefa 'notifyApproved' NÃO deveria ter rodado.");
            assertEquals(ProcessInstanceStatus.COMPLETED, instance.status());
        }
    }
}
