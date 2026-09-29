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

/**
 * Falha de validação de uma variável vinculada a um nó.
 *
 * @param variable {@code key} da variável.
 * @param code     um de {@link #REQUIRED}, {@link #INVALID_FORMAT}, {@link #INVALID_OPTION},
 *                 {@link #PATTERN_MISMATCH}, {@link #UNDECLARED}.
 * @param message  mensagem legível (para {@code PATTERN_MISMATCH}, o {@code validationMessage} declarado, se houver).
 */
public record VariableValidationError(String variable, String code, String message) {

    public static final String REQUIRED = "REQUIRED";
    public static final String INVALID_FORMAT = "INVALID_FORMAT";
    public static final String INVALID_OPTION = "INVALID_OPTION";
    public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
    /** Vínculo aponta para uma variável fora do catálogo — só alcançável se o deploy não validou a definição. */
    public static final String UNDECLARED = "UNDECLARED";
}
