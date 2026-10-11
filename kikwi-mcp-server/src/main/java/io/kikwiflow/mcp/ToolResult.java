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
 * Resultado de uma ferramenta: {@code summary} é uma frase para o agente (e para o humano que lê o log da conversa);
 * {@code data} vira o {@code structuredContent} — e também é serializado no conteúdo de texto, para clientes que
 * ainda não leem conteúdo estruturado.
 */
public record ToolResult(String summary, Object data) {

    public static ToolResult of(String summary, Object data) {
        return new ToolResult(summary, data);
    }
}
