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
package io.kikwiflow.model.definition.process.variable;

import java.util.List;

/**
 * Contrato de dados de um nó.
 * <ul>
 *   <li>{@code inputs} — dados que o ator fornece: para o start, exigidos para iniciar a instância; para uma
 *   {@code EXTERNAL_TASK}, exigidos para concluí-la. É o que as APIs de formulário descrevem.</li>
 *   <li>{@code outputs} — asserção de que um dado existe após a etapa. Modelado e validado no deploy, mas
 *   ainda não aplicado em runtime (fase 2, ver docs/engine/26).</li>
 * </ul>
 */
public record VariableBindings(List<VariableBinding> inputs, List<VariableBinding> outputs) {

    public VariableBindings {
        inputs = inputs != null ? List.copyOf(inputs) : List.of();
        outputs = outputs != null ? List.copyOf(outputs) : List.of();
    }

    public static VariableBindings empty() {
        return new VariableBindings(List.of(), List.of());
    }
}
