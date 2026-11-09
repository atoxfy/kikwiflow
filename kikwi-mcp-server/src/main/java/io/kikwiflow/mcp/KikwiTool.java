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

import java.util.Map;

/**
 * Uma ferramenta MCP do Kikwiflow, independente do SDK — só {@link KikwiMcpServerFactory} conhece o SDK. Isso
 * permite trocar o transporte (ex.: protocolo stateless 2026-07-28) sem tocar nas ferramentas.
 *
 * @param readOnly    vira {@code readOnlyHint}; ferramentas de leitura nunca mudam estado do motor.
 * @param destructive vira {@code destructiveHint}; clientes MCP pedem aprovação humana antes de chamar.
 * @param write       ferramenta de escrita: só registrada com {@code kikwiflow.mcp.write-tools.enabled=true} e
 *                    recusada para identidade anônima (salvo configuração explícita).
 */
public record KikwiTool(String name,
                        String title,
                        String description,
                        Map<String, Object> inputSchema,
                        boolean readOnly,
                        boolean destructive,
                        boolean idempotent,
                        boolean write,
                        ToolHandler handler) {

    @FunctionalInterface
    public interface ToolHandler {
        ToolResult handle(ToolArgs args, McpCallContext context);
    }

    public static KikwiTool read(String name, String title, String description, Map<String, Object> inputSchema,
                                 ToolHandler handler) {
        return new KikwiTool(name, title, description, inputSchema, true, false, true, false, handler);
    }
}
