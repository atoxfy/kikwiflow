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
import java.util.Map;

/**
 * Entrada do catálogo de variáveis de um processo ({@code ProcessDefinition.variableDeclarations()}).
 * Declarada uma vez por processo; nós a referenciam por {@code key} via {@link VariableBinding}.
 *
 * @param key                 nome da {@code ProcessVariable} correspondente; único no processo.
 * @param label               texto amigável exibido no formulário.
 * @param description         texto de ajuda opcional.
 * @param format              formato semântico/de renderização.
 * @param options             obrigatório para {@link VariableFormat#isSelect() selects}; proibido nos demais.
 * @param currency            código ISO-4217, obrigatório para {@link VariableFormat#MONEY}.
 * @param validationRegex     opcional, só para formatos de texto; o valor inteiro precisa casar.
 * @param validationMessage   mensagem devolvida quando {@code validationRegex} não casa.
 * @param extensionProperties dicas livres para o frontend (placeholder, máscara...), ignoradas pelo motor.
 */
public record VariableDeclaration(String key,
                                  String label,
                                  String description,
                                  VariableFormat format,
                                  List<VariableOption> options,
                                  String currency,
                                  String validationRegex,
                                  String validationMessage,
                                  Map<String, String> extensionProperties) {

    public VariableDeclaration {
        options = options != null ? List.copyOf(options) : List.of();
        extensionProperties = extensionProperties != null ? Map.copyOf(extensionProperties) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String key;
        private String label;
        private String description;
        private VariableFormat format;
        private List<VariableOption> options;
        private String currency;
        private String validationRegex;
        private String validationMessage;
        private Map<String, String> extensionProperties;

        private Builder() {}

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder format(VariableFormat format) {
            this.format = format;
            return this;
        }

        public Builder options(List<VariableOption> options) {
            this.options = options;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder validationRegex(String validationRegex) {
            this.validationRegex = validationRegex;
            return this;
        }

        public Builder validationMessage(String validationMessage) {
            this.validationMessage = validationMessage;
            return this;
        }

        public Builder extensionProperties(Map<String, String> extensionProperties) {
            this.extensionProperties = extensionProperties;
            return this;
        }

        public VariableDeclaration build() {
            return new VariableDeclaration(key, label, description, format, options, currency, validationRegex,
                    validationMessage, extensionProperties);
        }
    }
}
