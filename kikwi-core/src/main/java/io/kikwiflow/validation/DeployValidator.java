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
package io.kikwiflow.validation;

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.execution.DecisionRuleResolver;
import io.kikwiflow.execution.DelegateResolver;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ServiceTaskDefinition;

/**
 * Validates a ProcessDefinition at deploy-time to ensure all its required
 * dependencies (e.g., Spring beans for delegates and rules) are available.
 */
public class DeployValidator {

    private final DelegateResolver delegateResolver;
    private final DecisionRuleResolver decisionRuleResolver;

    public DeployValidator(DelegateResolver delegateResolver, DecisionRuleResolver decisionRuleResolver) {
        this.delegateResolver = delegateResolver;
        this.decisionRuleResolver = decisionRuleResolver;
    }

    public void validate(ProcessDefinition definition) {
        definition.flowNodes().values().forEach(node -> {
            if (node instanceof ServiceTaskDefinition serviceTask) {
                String delegateExpression = serviceTask.delegateExpression();
                if (delegateExpression != null && !delegateExpression.isBlank()) {
                    try {
                        delegateResolver.resolve(delegateExpression);
                    } catch (Exception e) {
                        throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for Service Task '%s' (id: %s): Delegate bean '%s' not found in application context.",
                                serviceTask.name(), serviceTask.id(), delegateExpression), e);
                    }
                }
            } else if (node instanceof ExclusiveGatewayDefinition gateway) {
                gateway.outgoing().forEach(flow -> {
                    String ruleKey = flow.condition();
                    if (ruleKey != null && !ruleKey.isBlank()) {
                        try {
                            decisionRuleResolver.resolve(ruleKey);
                        } catch (Exception e) {
                            throw new InvalidProcessDefinitionException(
                                String.format("Validation failed for Gateway '%s' (id: %s): DecisionRule bean '%s' not found for sequence flow '%s'.",
                                    gateway.name(), gateway.id(), ruleKey, flow.id()), e);
                        }
                    }
                });
            }
        });
    }
}