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

package io.kikwiflow.mcp.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@code kikwiflow.mcp.*} — servidor MCP do Kikwiflow. Ver docs/engine/30-servidor-mcp.md. */
@ConfigurationProperties(prefix = "kikwiflow.mcp")
public class KikwiMcpProperties {

    /** Liga o servidor MCP. Desligado por padrão: expor o motor a agentes é uma decisão explícita do host. */
    private boolean enabled = false;

    /** Caminho do endpoint. Vazio = {@code ${kikwiflow.rest.base-path}/mcp}, herdando CORS e filtros do REST. */
    private String path;

    /**
     * URL pública do host (ex.: https://ops.minhaempresa.com) para os deep links do Monitor. Vazio = usa
     * {@code kikwiflow.monitor-ui.api-url} quando absoluta; senão, derivada de cada requisição (atrás de proxy, ligue
     * {@code server.forward-headers-strategy}).
     */
    private String publicBaseUrl;

    /** Inclui {@code monitorUrl}/{@code historyUrl} nos resultados (quando o Monitor embutido está instalado). */
    private boolean monitorLinks = true;

    /**
     * Origens de navegador aceitas (além de {@code kikwiflow.rest.cors.allowed-origins}). Requisições com cabeçalho
     * {@code Origin} fora da lista são recusadas (proteção contra DNS rebinding exigida pela spec MCP); clientes
     * não-navegador (Claude Code, IDEs) não mandam {@code Origin} e não são afetados.
     */
    private List<String> allowedOrigins = new ArrayList<>();

    private final Variables variables = new Variables();
    private final WriteTools writeTools = new WriteTools();
    private final Confirmation confirmation = new Confirmation();

    public static class Variables {
        /** Valores de variáveis maiores que isso são truncados nas respostas (protege o contexto do modelo). */
        private int maxValueChars = 2000;

        public int getMaxValueChars() { return maxValueChars; }
        public void setMaxValueChars(int maxValueChars) { this.maxValueChars = maxValueChars; }
    }

    public static class WriteTools {
        /** Registra start_process_instance e retry_incidents. Desligado = servidor somente leitura. */
        private boolean enabled = false;
        /** Permite escrita com identidade anônima. Só para demo/local — em produção a escrita exige usuário. */
        private boolean allowAnonymous = false;
        /** Máximo de incidentes por lote em retry_incidents. */
        private int maxBatchRetry = 20;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAllowAnonymous() { return allowAnonymous; }
        public void setAllowAnonymous(boolean allowAnonymous) { this.allowAnonymous = allowAnonymous; }
        public int getMaxBatchRetry() { return maxBatchRetry; }
        public void setMaxBatchRetry(int maxBatchRetry) { this.maxBatchRetry = maxBatchRetry; }
    }

    public static class Confirmation {
        /**
         * Segredo HMAC dos tokens de confirmação (mín. 32 caracteres). Vazio = gerado na subida, válido só neste nó —
         * em cluster configure o mesmo valor em todos os nós.
         */
        private String secret;
        /** Validade do token entre o preview e a execução. */
        private Duration ttl = Duration.ofMinutes(5);

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
    public boolean isMonitorLinks() { return monitorLinks; }
    public void setMonitorLinks(boolean monitorLinks) { this.monitorLinks = monitorLinks; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Variables getVariables() { return variables; }
    public WriteTools getWriteTools() { return writeTools; }
    public Confirmation getConfirmation() { return confirmation; }
}
