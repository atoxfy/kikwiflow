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

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Toda variável de processo que sai pelo MCP passa por aqui: política de leitura/mascaramento do host
 * ({@link VariableSecurityPolicyManager}) e truncamento de valores grandes, para não estourar o contexto do modelo
 * (ex.: a resposta bruta de um LLM guardada para auditoria).
 */
public class VariablePresenter {

    private final VariableSecurityPolicyManager securityPolicyManager;
    private final int maxValueChars;

    public VariablePresenter(VariableSecurityPolicyManager securityPolicyManager, int maxValueChars) {
        this.securityPolicyManager = securityPolicyManager;
        this.maxValueChars = maxValueChars;
    }

    /** Aplica a política de leitura e devolve {@code nome → valor} em ordem alfabética. */
    public Map<String, Object> present(String processDefinitionId, IdentityContext identity,
                                       Map<String, ProcessVariable> variables) {
        Map<String, Object> result = new TreeMap<>();
        if (variables == null || variables.isEmpty()) {
            return result;
        }
        Map<String, ProcessVariable> visible = securityPolicyManager.applyReadPoliciesAndMasking(
                processDefinitionId, identity, variables);
        if (visible != null) {
            visible.forEach((name, variable) -> result.put(name, variable != null ? limit(variable.value()) : null));
        }
        return result;
    }

    /** Variáveis que já passaram pela política (ex.: trace do histórico) — só truncamento. */
    public Map<String, Object> presentAlreadyMasked(Map<String, ProcessVariable> variables) {
        Map<String, Object> result = new TreeMap<>();
        if (variables != null) {
            variables.forEach((name, variable) -> result.put(name, variable != null ? limit(variable.value()) : null));
        }
        return result;
    }

    public Object limit(Object value) {
        if (value instanceof byte[] bytes) {
            return "[binário, " + bytes.length + " bytes]";
        }
        if (value instanceof CharSequence text) {
            return truncate(text.toString());
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) {
            String rendered = String.valueOf(value);
            return rendered.length() > maxValueChars ? truncate(rendered) : value;
        }
        return value;
    }

    public String truncate(String text) {
        if (text == null || text.length() <= maxValueChars) {
            return text;
        }
        return text.substring(0, maxValueChars) + "… [truncado: +" + (text.length() - maxValueChars) + " caracteres]";
    }
}
