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

/**
 * Vínculo de um nó a uma entrada do catálogo {@code variableDeclarations} do processo, por {@code key} —
 * nunca redeclara label/formato. {@code required} ausente equivale a {@code false}: o campo aparece no
 * formulário e, se preenchido, tem o formato validado, mas não bloqueia.
 */
public record VariableBinding(String variable, Boolean required) {

    /** Não usa prefixo {@code is}: evitaria colisão com a propriedade {@code required} na serialização Jackson. */
    public boolean requiresValue() {
        return Boolean.TRUE.equals(required);
    }
}
