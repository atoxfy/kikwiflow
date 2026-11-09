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

package io.kikwiflow.execution.policy;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.api.retry.RetryPolicyEvaluator.RetryEvaluationResult;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.execution.enumerated.RetryStrategy;
import io.kikwiflow.model.execution.node.ExecutableTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sem jitter, tarefas que falham juntas (a mesma API externa fora do ar) voltam todas no mesmo instante, em todas
 * as tentativas. O "equal jitter" sorteia o atraso entre 50% e 100% do intervalo do EXPONENTIAL_BACKOFF — nunca
 * acima dele, então o {@code maxInterval} continua sendo teto.
 */
@DisplayName("Dado um EXPONENTIAL_BACKOFF de PT10S, multiplicador 2, teto PT1M, na 3ª execução (intervalo base 40s)")
class DefaultRetryPolicyEvaluatorJitterTest {

    private static final RetryPolicy POLICY =
            new RetryPolicy(RetryStrategy.EXPONENTIAL_BACKOFF, 5, "PT10S", 2.0, "PT1M", List.of());
    private static final long TOLERANCE_MILLIS = 1_000;

    private static ExecutableTask taskWithExecutions(long executions) {
        return ExecutableTask.builder().id("task-1").retries(5L).executions(executions).build();
    }

    private static Duration delayOf(DefaultRetryPolicyEvaluator evaluator, ExecutableTask task) {
        Instant before = Instant.now();
        RetryEvaluationResult result = evaluator.evaluate(task, new RuntimeException("API fora do ar"), POLICY);
        assertFalse(result.shouldCreateIncident());
        assertEquals(4L, result.retriesLeft());
        return Duration.between(before, result.nextDueDate());
    }

    private static void assertDelayBetween(Duration delay, long minMillis, long maxMillis) {
        assertTrue(delay.toMillis() >= minMillis && delay.toMillis() <= maxMillis + TOLERANCE_MILLIS,
                "atraso " + delay.toMillis() + "ms fora de [" + minMillis + ", " + maxMillis + "]");
    }

    private static DefaultRetryPolicyEvaluator evaluator(boolean jitterEnabled, DoubleSupplier random) {
        KikwiflowConfig config = new KikwiflowConfig();
        config.setRetryJitterEnabled(jitterEnabled);
        return new DefaultRetryPolicyEvaluator(config, random);
    }

    @Nested
    @DisplayName("Quando o jitter está ligado (padrão)")
    class QuandoLigado {

        @Test
        @DisplayName("Então o sorteio mínimo agenda em 50% do intervalo (20s)")
        void sorteioMinimoEmMetade() {
            assertDelayBetween(delayOf(evaluator(true, () -> 0.0), taskWithExecutions(2)), 20_000, 20_000);
        }

        @Test
        @DisplayName("Então o sorteio máximo agenda no intervalo cheio (40s), nunca acima")
        void sorteioMaximoNoIntervaloCheio() {
            assertDelayBetween(delayOf(evaluator(true, () -> 0.999_999), taskWithExecutions(2)), 39_999, 40_000);
        }

        @Test
        @DisplayName("Então o maxInterval continua sendo teto mesmo com o sorteio máximo")
        void respeitaMaxInterval() {
            // 10s * 2^10 passaria de uma hora; o teto é 1 minuto.
            assertDelayBetween(delayOf(evaluator(true, () -> 0.999_999), taskWithExecutions(10)), 59_999, 60_000);
        }

        @Test
        @DisplayName("Então tarefas que falham juntas são espalhadas, não reagendadas todas no mesmo instante")
        void espalhaTarefasQueFalhamJuntas() {
            DefaultRetryPolicyEvaluator evaluator = new DefaultRetryPolicyEvaluator(new KikwiflowConfig());
            Set<Long> distinctDelays = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                Duration delay = delayOf(evaluator, taskWithExecutions(2));
                assertDelayBetween(delay, 20_000, 40_000);
                distinctDelays.add(delay.toMillis() / 100);
            }
            assertTrue(distinctDelays.size() > 10, "esperava atrasos espalhados, vieram " + distinctDelays.size());
        }
    }

    @Nested
    @DisplayName("Quando o jitter está desligado (kikwiflow.retry.jitter-enabled=false)")
    class QuandoDesligado {

        @Test
        @DisplayName("Então o atraso é exatamente o do backoff (40s), como antes")
        void atrasoDeterministico() {
            assertDelayBetween(delayOf(evaluator(false, () -> 0.0), taskWithExecutions(2)), 40_000, 40_000);
        }
    }
}
