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
package io.kikwiflow.model.execution.form;

import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;

import java.util.List;
import java.util.Map;

/**
 * Um campo de formulário server-driven: a {@code VariableDeclaration} do catálogo achatada com o
 * {@code required} do vínculo do nó e, quando há instância, o valor atual da variável.
 */
public record FormField(String key,
                        String label,
                        String description,
                        VariableFormat format,
                        boolean required,
                        List<VariableOption> options,
                        String currency,
                        String validationRegex,
                        String validationMessage,
                        Map<String, String> extensionProperties,
                        Object value) {
}
