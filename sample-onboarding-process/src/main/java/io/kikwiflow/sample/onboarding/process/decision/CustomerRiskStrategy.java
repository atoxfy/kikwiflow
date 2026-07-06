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

package io.kikwiflow.sample.onboarding.process.decision;

import io.kikwiflow.execution.api.context.EvaluationContext;
import io.kikwiflow.execution.api.provider.AnswerProvider;
import io.kikwiflow.sample.onboarding.process.VariableScope;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CustomerRiskStrategy implements AnswerProvider {

    @Override
    public String resolve(EvaluationContext context) {
        return context.getVariableValue(VariableScope.RISK_SCORE)
                .filter(Objects::nonNull)
                .map(riskScore -> (Double) riskScore < 50 ? "FRAUDE" : "APROVADO")
                .orElse(null);
    }
}
