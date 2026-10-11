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

package io.kikwiflow.mcp.support;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descrição de um nó voltada ao modelo: o que ele executa, como decide, que contrato de variáveis tem e, para nós
 * de IA, qual prompt/modelo usa. Omite layout e campos de canvas — isso é do Craft, não do agente.
 */
public final class NodeDescriber {

    public static final String DOCUMENTATION_KEY = "kikwi:documentation";
    public static final String PROMPT_KEY = "kikwi:prompt";
    public static final String LLM_MODEL_KEY = "kikwi:llm.model";

    private NodeDescriber() {
    }

    public static Map<String, Object> describe(ProcessDefinition definition, String nodeKey, FlowNodeDefinition node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", nodeKey);
        result.put("name", node.name());
        result.put("type", node.type());
        putIfPresent(result, "description", node.description());

        Map<String, String> extensions = node.extensionProperties() != null ? node.extensionProperties() : Map.of();
        putIfPresent(result, "documentation", extensions.get(DOCUMENTATION_KEY));

        if (node instanceof ExecutableTaskDefinition task) {
            putIfPresent(result, "executor", task.executor());
            putIfPresent(result, "retryPolicy", task.retryPolicy());
            if (extensions.containsKey(PROMPT_KEY)) {
                Map<String, Object> ai = new LinkedHashMap<>();
                ai.put("prompt", extensions.get(PROMPT_KEY));
                putIfPresent(ai, "model", extensions.get(LLM_MODEL_KEY));
                result.put("ai", ai);
            }
        }
        if (node instanceof ExclusiveGatewayDefinition gateway) {
            Map<String, Object> decision = new LinkedHashMap<>();
            putIfPresent(decision, "providerType", gateway.providerType());
            putIfPresent(decision, "providerBean", gateway.providerBean());
            putIfPresent(decision, "providerVariable", gateway.providerVariable());
            putIfPresent(decision, "defaultFlow", gateway.defaultFlow());
            result.put("decision", decision);
        }
        if (node instanceof VariableBindingAware aware) {
            VariableBindings bindings = aware.variableBindingsOrEmpty();
            if (!bindings.inputs().isEmpty()) {
                result.put("inputs", bindings(definition, bindings.inputs()));
            }
            if (!bindings.outputs().isEmpty()) {
                result.put("outputs", bindings(definition, bindings.outputs()));
            }
        }
        if (node.commitBefore() != null && node.commitBefore()) {
            result.put("async", true);
        }

        List<Map<String, Object>> outgoing = new ArrayList<>();
        if (node.outgoing() != null) {
            for (SequenceFlowDefinition flow : node.outgoing()) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("to", flow.targetNodeId());
                putIfPresent(edge, "name", flow.name());
                putIfPresent(edge, "whenAnswer", flow.expectedAnswer());
                if (flow.isDefault()) {
                    edge.put("default", true);
                }
                outgoing.add(edge);
            }
        }
        result.put("next", outgoing);
        return result;
    }

    public static List<Map<String, Object>> bindings(ProcessDefinition definition, List<VariableBinding> bindings) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VariableBinding binding : bindings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("variable", binding.variable());
            item.put("required", binding.requiresValue());
            definition.findVariableDeclaration(binding.variable()).ifPresent(declaration -> {
                putIfPresent(item, "label", declaration.label());
                putIfPresent(item, "format", declaration.format());
            });
            result.add(item);
        }
        return result;
    }

    public static Map<String, Object> declaration(VariableDeclaration declaration) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", declaration.key());
        putIfPresent(item, "label", declaration.label());
        putIfPresent(item, "description", declaration.description());
        putIfPresent(item, "format", declaration.format());
        if (declaration.options() != null && !declaration.options().isEmpty()) {
            item.put("options", declaration.options());
        }
        putIfPresent(item, "currency", declaration.currency());
        putIfPresent(item, "validationRegex", declaration.validationRegex());
        // Como o número está gravado quando o host guarda texto (ex.: "45.000,00") — a agregação usa como padrão.
        if (declaration.extensionProperties() != null) {
            putIfPresent(item, "numberFormat", declaration.extensionProperties().get(VariableNumberFormat.CATALOG_PROPERTY));
        }
        return item;
    }

    public static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !(value instanceof String text && text.isBlank())) {
            target.put(key, value);
        }
    }
}
