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
import io.kikwiflow.exception.TaskHandlerNotFoundException;
import io.kikwiflow.execution.TaskHandlerResolver;
import io.kikwiflow.execution.api.provider.AnswerProvider;
import io.kikwiflow.execution.api.resolver.AnswerProviderResolver;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;

/**
 * Validates a ProcessDefinition at deploy-time to ensure all its required
 * dependencies (e.g., Spring beans for task handlers and rules) are available.
 */
public class DeployValidator {

    private final TaskHandlerResolver taskHandlerResolver;
    private final AnswerProviderResolver answerProviderResolver;

    public DeployValidator(TaskHandlerResolver taskHandlerResolver, AnswerProviderResolver answerProviderResolver) {
        this.taskHandlerResolver = taskHandlerResolver;
        this.answerProviderResolver = answerProviderResolver;
    }

    public void validate(ProcessDefinition definition) {
        definition.flowNodes().values().forEach(node -> {
            if (node instanceof ExecutableTaskDefinition serviceTask) {
                String executor = serviceTask.executor();
                if (executor != null && !executor.isBlank()) {
                    try {
                        taskHandlerResolver.resolve(executor)
                                .orElseThrow(() -> new TaskHandlerNotFoundException(""));
                    } catch (Exception e) {
                        throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for Service Task '%s' (id: %s): Task Handler bean '%s' not found in application context.",
                                serviceTask.name(), serviceTask.id(), executor), e);
                    }
                }
            } else if (node instanceof ExclusiveGatewayDefinition gateway) {
                // Validação do Provedor de Resposta
                if (gateway.providerType() == AnswerProviderType.BEAN) {
                    String beanName = gateway.providerBean();
                    if (beanName == null || beanName.isBlank()) {
                        throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Configured as BEAN but 'providerBean' is empty.", gateway.id()));
                    }
                    try {
                        AnswerProvider provider = answerProviderResolver.getProvider(beanName).orElseThrow(() -> new InvalidProcessDefinitionException(String.format("AnswerProvider bean '%s' not found.", beanName)));

                    } catch (Exception e) {
                        throw new InvalidProcessDefinitionException(String.format("AnswerProvider bean '%s' not found or could not be instantiated.", beanName), e);
                    }
                } else if (gateway.providerType() == AnswerProviderType.VARIABLE) {
                    if (gateway.providerVariable() == null || gateway.providerVariable().isBlank()) {
                        throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Configured as VARIABLE but 'providerVariable' is empty.", gateway.id()));
                    }
                } else {
                    throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': 'providerType' is missing.", gateway.id()));
                }

                // Validação Estrutural das Arestas (Sequence Flows)
                long defaultFlowsCount = gateway.outgoing().stream().filter(SequenceFlowDefinition::isDefault).count();
                if (defaultFlowsCount > 1) {
                    throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Multiple sequence flows are marked as default.", gateway.id()));
                }
            }
        });
    }
}