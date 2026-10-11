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

package io.kikwiflow.mcp.support;

import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.model.security.IdentityContext;

import java.util.Objects;

/**
 * Escopo de tenant das ferramentas: quem chama com um tenant definido (e não é {@code GLOBAL}/sistema) só enxerga
 * dados desse tenant. Recursos de outro tenant respondem {@code NOT_FOUND} — não revelamos que existem.
 */
public final class TenantGuard {

    public static final String GLOBAL = "GLOBAL";

    private TenantGuard() {
    }

    /** @return o tenant a forçar nas consultas, ou {@code null} quando a identidade enxerga todos. */
    public static String scopedTenant(IdentityContext identity) {
        if (identity == null || identity.isSystem()) {
            return null;
        }
        String tenant = identity.tenantId();
        return tenant == null || tenant.isBlank() || GLOBAL.equals(tenant) ? null : tenant;
    }

    public static void checkAccess(IdentityContext identity, String resourceTenant, String notFoundMessage) {
        String scoped = scopedTenant(identity);
        if (scoped != null && !Objects.equals(scoped, resourceTenant)) {
            throw ToolException.notFound(notFoundMessage);
        }
    }
}
