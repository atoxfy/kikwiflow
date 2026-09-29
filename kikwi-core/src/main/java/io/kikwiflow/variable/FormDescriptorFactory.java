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
package io.kikwiflow.variable;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.form.FormDescriptor;
import io.kikwiflow.model.execution.form.FormField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta o {@link FormDescriptor} de um nó a partir dos seus {@code inputs} e do catálogo do processo —
 * a base das APIs de formulário server-driven. Vínculos que não resolvem no catálogo são ignorados (o deploy
 * já os rejeita).
 */
public final class FormDescriptorFactory {

    private FormDescriptorFactory() {}

    /** Formulário de start: sem valores atuais (não existe instância ainda). */
    public static FormDescriptor forStart(ProcessDefinition processDefinition) {
        FlowNodeDefinition startNode = processDefinition.flowNodes().get(processDefinition.defaultStartPoint());
        return forNode(processDefinition, processDefinition.defaultStartPoint(), startNode, Map.of());
    }

    /**
     * @param currentVariables variáveis atuais da instância, usadas para pré-preencher {@link FormField#value()};
     *                         pode ser vazio.
     */
    public static FormDescriptor forNode(ProcessDefinition processDefinition, String nodeId, FlowNodeDefinition node,
                                         Map<String, ProcessVariable> currentVariables) {
        List<FormField> fields = new ArrayList<>();
        String nodeName = node != null ? node.name() : null;

        if (node instanceof VariableBindingAware aware) {
            for (VariableBinding binding : aware.variableBindingsOrEmpty().inputs()) {
                processDefinition.findVariableDeclaration(binding.variable()).ifPresent(declaration ->
                        fields.add(toField(declaration, binding, currentVariables)));
            }
        }

        return new FormDescriptor(processDefinition.id(), processDefinition.key(), nodeId, nodeName, fields);
    }

    private static FormField toField(VariableDeclaration declaration, VariableBinding binding,
                                     Map<String, ProcessVariable> currentVariables) {
        ProcessVariable current = currentVariables != null ? currentVariables.get(declaration.key()) : null;
        return new FormField(
                declaration.key(),
                declaration.label(),
                declaration.description(),
                declaration.format(),
                binding.requiresValue(),
                declaration.options(),
                declaration.currency(),
                declaration.validationRegex(),
                declaration.validationMessage(),
                declaration.extensionProperties(),
                current != null ? current.value() : null);
    }
}
