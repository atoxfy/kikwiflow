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
package io.kikwiflow.model.definition.process;

import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * O contrato de um processo visto de fora — de quem o chama como ferramenta (um agente, hoje; um call activity ou
 * um gateway por alias, depois).
 * <ul>
 *   <li>{@code inputs} — os {@code inputs} do start: o que é preciso para iniciar.</li>
 *   <li>{@code outputs} — a união dos {@code outputs} dos fins: o que o processo devolve. Quem chama enxerga só
 *   isso, nunca o estado inteiro da instância. Um campo é obrigatório só quando todos os fins o exigem — um fim
 *   que não o declara pode terminar sem ele.</li>
 * </ul>
 * Cada campo traz a declaração do catálogo, para quem chama montar schema (formato, opções, rótulo) sem
 * reinterpretar o processo.
 */
public record ProcessContract(List<Field> inputs, List<Field> outputs) {

    public ProcessContract {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public record Field(VariableDeclaration declaration, boolean required) {

        public String variable() {
            return declaration.key();
        }
    }

    public static ProcessContract of(ProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");

        List<Field> inputs = new ArrayList<>();
        FlowNodeDefinition start = definition.defaultStartPoint() == null ? null
                : definition.flowNodes().get(definition.defaultStartPoint());
        if (start instanceof VariableBindingAware aware) {
            for (VariableBinding binding : aware.variableBindingsOrEmpty().inputs()) {
                definition.findVariableDeclaration(binding.variable())
                        .ifPresent(declaration -> inputs.add(new Field(declaration, binding.requiresValue())));
            }
        }

        List<EndEventDefinition> ends = definition.flowNodes().values().stream()
                .filter(EndEventDefinition.class::isInstance)
                .map(EndEventDefinition.class::cast)
                // flowNodes não tem ordem garantida; ordenar pelo id deixa o contrato (e o schema gerado a partir
                // dele) estável entre leituras da mesma versão.
                .sorted(Comparator.comparing(EndEventDefinition::id))
                .toList();

        // variável -> exigida em todos os fins até agora; contagem de fins que a declaram
        Map<String, Boolean> requiredEverywhere = new LinkedHashMap<>();
        Map<String, Integer> declaredIn = new LinkedHashMap<>();
        for (EndEventDefinition end : ends) {
            for (VariableBinding binding : end.variableBindingsOrEmpty().outputs()) {
                requiredEverywhere.merge(binding.variable(), binding.requiresValue(), Boolean::logicalAnd);
                declaredIn.merge(binding.variable(), 1, Integer::sum);
            }
        }

        List<Field> outputs = new ArrayList<>();
        requiredEverywhere.forEach((variable, required) -> definition.findVariableDeclaration(variable)
                .ifPresent(declaration -> outputs.add(new Field(declaration,
                        required && declaredIn.get(variable) == ends.size()))));

        return new ProcessContract(inputs, outputs);
    }

    /** O que volta para quem chamou: só as variáveis de {@code outputs} presentes em {@code variables}. */
    public <V> Map<String, V> project(Map<String, V> variables) {
        Map<String, V> projected = new LinkedHashMap<>();
        if (variables == null) {
            return projected;
        }
        for (Field output : outputs) {
            V value = variables.get(output.variable());
            if (value != null) {
                projected.put(output.variable(), value);
            }
        }
        return projected;
    }

    public boolean returnsSomething() {
        return !outputs.isEmpty();
    }
}
