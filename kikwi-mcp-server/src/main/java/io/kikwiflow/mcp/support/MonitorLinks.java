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

import io.kikwiflow.mcp.McpCallContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Deep links para o Kikwiflow Monitor embutido. As rotas espelham as do static export
 * ({@code /monitor?processId=&instance=}, {@code /monitor/history?instanceId=}). Com {@code monitorPath} nulo
 * (Monitor não instalado) todos os métodos devolvem {@code null} e o campo some do JSON.
 */
public final class MonitorLinks {

    private final String monitorPath;

    public MonitorLinks(String monitorPath) {
        this.monitorPath = monitorPath == null || monitorPath.isBlank() ? null : trimTrailingSlash(monitorPath);
    }

    public static MonitorLinks disabled() {
        return new MonitorLinks(null);
    }

    public String process(McpCallContext context, String processDefinitionId) {
        if (monitorPath == null || processDefinitionId == null) {
            return null;
        }
        return root(context) + "/monitor?processId=" + encode(processDefinitionId);
    }

    public String instance(McpCallContext context, String processDefinitionId, String processInstanceId) {
        String process = process(context, processDefinitionId);
        return process == null || processInstanceId == null ? process : process + "&instance=" + encode(processInstanceId);
    }

    public String history(McpCallContext context, String processInstanceId) {
        if (monitorPath == null || processInstanceId == null) {
            return null;
        }
        return root(context) + "/monitor/history?instanceId=" + encode(processInstanceId);
    }

    private String root(McpCallContext context) {
        return trimTrailingSlash(context.baseUrl()) + monitorPath;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
