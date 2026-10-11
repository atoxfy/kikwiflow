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

import io.kikwiflow.model.security.IdentityContext;

import java.util.Set;

/**
 * Contexto de uma chamada MCP: quem chama (resolvido pelo mesmo {@code HttpIdentityResolver} do REST) e a URL
 * pública do host, usada para montar deep links do Monitor.
 */
public record McpCallContext(IdentityContext identity, String baseUrl) {

    public static final String ANONYMOUS_ACTOR = "anonymous";

    public McpCallContext {
        if (identity == null) {
            identity = new IdentityContext(ANONYMOUS_ACTOR, null, Set.of());
        }
        if (baseUrl == null) {
            baseUrl = "";
        }
    }

    public boolean isAnonymous() {
        String actor = identity.actorId();
        return actor == null || actor.isBlank() || ANONYMOUS_ACTOR.equals(actor);
    }
}
