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
import io.kikwiflow.execution.api.retry.RetryPolicyEvaluator;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.execution.enumerated.RetryStrategy;
import io.kikwiflow.model.execution.node.ExecutableTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class DefaultRetryPolicyEvaluator implements RetryPolicyEvaluator {

    private final KikwiflowConfig config;
    private final Set<String> fatalExceptionsSet;

    public DefaultRetryPolicyEvaluator(KikwiflowConfig config) {
        this.config = config;
        this.fatalExceptionsSet = config.getFatalExceptions() != null
                ? new java.util.HashSet<>(config.getFatalExceptions())
                : java.util.Collections.emptySet();
    }

    @Override
    public RetryEvaluationResult evaluate(ExecutableTask task, Exception exception, RetryPolicy policy) {
        Throwable rootCause = exception.getCause() != null ? exception.getCause() : exception;
        if (fatalExceptionsSet.contains(rootCause.getClass().getName())) {
            return new RetryEvaluationResult(0L, Instant.now(), true);
        }

        if (policy == null) {
            long retriesLeft = task.retries() - 1;

            Duration defaultInterval = Duration.parse(config.getDefaultRetryInterval());
            Instant nextRetry = Instant.now().plus(defaultInterval);
            return new RetryEvaluationResult(retriesLeft, nextRetry, retriesLeft <= 0);
        }

        long retriesLeft = task.retries() - 1;
        if (retriesLeft <= 0) {
            return new RetryEvaluationResult(0L, Instant.now(), true);
        }

        int attemptIndex = task.executions() != null ? task.executions().intValue() : 0;
        Instant nextDueDate = Instant.now();

        if (policy.strategy() == RetryStrategy.LINEAR) {
            nextDueDate = calculateLinear(policy, attemptIndex);
        }
        else if (policy.strategy() == RetryStrategy.EXPONENTIAL_BACKOFF) {
            nextDueDate = calculateExponential(policy, attemptIndex);
        }

        return new RetryEvaluationResult(retriesLeft, nextDueDate, false);
    }

    private Instant calculateLinear(RetryPolicy policy, int attemptIndex) {
        if (policy.intervals() == null || policy.intervals().isEmpty()) {
            return Instant.now().plus(Duration.ofMinutes(1));
        }
        int index = Math.min(attemptIndex, policy.intervals().size() - 1);
        Duration interval = Duration.parse(policy.intervals().get(index));
        return Instant.now().plus(interval);
    }

    private Instant calculateExponential(RetryPolicy policy, int attemptIndex) {
        Duration initial = Duration.parse(policy.initialInterval());
        double multiplier = policy.multiplier() != null ? policy.multiplier() : 2.0;

        long calculatedMillis = (long) (initial.toMillis() * Math.pow(multiplier, attemptIndex));
        Duration calculatedDuration = Duration.ofMillis(calculatedMillis);

        if (policy.maxInterval() != null) {
            Duration max = Duration.parse(policy.maxInterval());
            if (calculatedDuration.compareTo(max) > 0) {
                calculatedDuration = max;
            }
        }

        return Instant.now().plus(calculatedDuration);
    }
}