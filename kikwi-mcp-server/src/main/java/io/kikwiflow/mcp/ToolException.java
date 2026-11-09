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

package io.kikwiflow.mcp;

/**
 * Erro que o agente deve ler e corrigir (argumento inválido, recurso inexistente, contrato violado...). Vira um
 * resultado {@code isError=true} — não um erro de protocolo — para o modelo poder ajustar a próxima chamada.
 * Os códigos espelham o envelope de erro do REST ({@code NOT_FOUND}, {@code CONFLICT}...).
 */
public class ToolException extends RuntimeException {

    public static final String INVALID_ARGUMENT = "INVALID_ARGUMENT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String CONTRACT_VIOLATION = "CONTRACT_VIOLATION";
    public static final String INVALID_CONFIRMATION = "INVALID_CONFIRMATION";

    private final String code;
    private final transient Object details;

    public ToolException(String code, String message) {
        this(code, message, null);
    }

    public ToolException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public static ToolException invalidArgument(String message) {
        return new ToolException(INVALID_ARGUMENT, message);
    }

    public static ToolException notFound(String message) {
        return new ToolException(NOT_FOUND, message);
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
