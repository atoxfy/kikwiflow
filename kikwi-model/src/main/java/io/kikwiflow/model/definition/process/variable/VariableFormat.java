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
 * Formato semântico de uma {@link VariableDeclaration}. É ao mesmo tempo a dica de renderização para um
 * frontend server-driven (qual componente mostrar) e a regra de checagem de formato aplicada pelo motor
 * quando a variável é vinculada a um nó. Enum fechado de propósito: nunca um nome de classe Java livre vindo
 * do JSON de deploy (ver docs/engine/26).
 */
public enum VariableFormat {
    /** Texto de uma linha. Valor: {@code String}. */
    SHORT_TEXT,
    /** Texto multi-linha. Valor: {@code String}. */
    LONG_TEXT,
    /** Número inteiro. Valor: {@code Integer}/{@code Long} ou string numérica. */
    INTEGER,
    /** Número decimal. Valor: qualquer {@code Number} ou string parseável por {@code BigDecimal}. */
    DECIMAL,
    /** Valor monetário na moeda fixa {@link VariableDeclaration#currency()}. Valor: como {@link #DECIMAL}. */
    MONEY,
    /** Valor: {@code Boolean}. */
    BOOLEAN,
    /** Data sem hora. Valor: {@code LocalDate} ou string ISO-8601 ({@code yyyy-MM-dd}). */
    DATE,
    /** Data e hora com offset. Valor: {@code OffsetDateTime}/{@code Instant} ou string ISO-8601. */
    DATETIME,
    /** Uma opção entre {@link VariableDeclaration#options()}. Valor: {@code String} (o {@code value} da opção). */
    SINGLE_SELECT,
    /** Várias opções entre {@link VariableDeclaration#options()}. Valor: coleção de {@code String}. */
    MULTI_SELECT;

    public boolean isText() {
        return this == SHORT_TEXT || this == LONG_TEXT;
    }

    public boolean isSelect() {
        return this == SINGLE_SELECT || this == MULTI_SELECT;
    }
}
