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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.management.service.ProcessInstanceSnapshotService;
import io.kikwiflow.mcp.KikwiMcpCatalog;
import io.kikwiflow.mcp.KikwiMcpResources;
import io.kikwiflow.mcp.KikwiMcpServerFactory;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.support.ConfirmationTokenService;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.rest.autoconfigure.properties.KikwiflowRestProperties;
import io.kikwiflow.security.HttpIdentityResolver;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servidor MCP do Kikwiflow (Streamable HTTP, stateless) no mesmo processo e atrás da mesma identidade do REST de
 * gestão. Liga com {@code kikwiflow.mcp.enabled=true}. Ver docs/engine/30-servidor-mcp.md.
 */
@AutoConfiguration(afterName = {
        "io.kikwiflow.starter.autoconfigure.KikwiflowAutoConfiguration",
        "io.kikwiflow.persistence.mongodb.autoconfigure.MongoDbPersistenceAutoConfiguration",
        "io.kikwiflow.query.autoconfigure.KikwiQueryAutoConfiguration",
        "io.kikwiflow.security.KikwiflowSecurityAutoConfiguration",
        "io.kikwiflow.rest.autoconfigure.KikwiRestAutoConfiguration"
})
@ConditionalOnClass({KikwiMcpServerFactory.class, HttpServletStatelessServerTransport.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "kikwiflow.mcp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KikwiMcpProperties.class)
public class KikwiMcpAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(KikwiMcpAutoConfiguration.class);

    /** Mesmo caminho fixo do Monitor embutido (basePath do static export). */
    static final String MONITOR_PATH = "/monitor-ui";
    static final String MONITOR_MARKER_CLASS = "io.kikwiflow.monitor.autoconfigure.KikwiMonitorAutoConfiguration";
    /** Endereço da API que o Monitor embutido usa no navegador — já é público por definição. */
    static final String MONITOR_API_URL_PROPERTY = "kikwiflow.monitor-ui.api-url";

    @Bean
    public ConfirmationTokenService kikwiMcpConfirmationTokenService(KikwiMcpProperties properties) {
        String secret = properties.getConfirmation().getSecret();
        byte[] key;
        if (secret == null || secret.isBlank()) {
            key = new byte[32];
            new SecureRandom().nextBytes(key);
            if (properties.getWriteTools().isEnabled()) {
                LOGGER.warn("kikwiflow.mcp.confirmation.secret não configurado: usando segredo aleatório deste nó. "
                        + "Em cluster (ou após um restart), um preview de retry_incidents gerado em um nó não será aceito "
                        + "em outro — configure o mesmo segredo (>= 32 caracteres) em todos os nós.");
            }
        } else {
            key = secret.getBytes(StandardCharsets.UTF_8);
        }
        return new ConfirmationTokenService(key, properties.getConfirmation().getTtl(), Clock.systemUTC());
    }

    @Bean
    public HttpServletStatelessServerTransport kikwiMcpTransport(KikwiMcpProperties properties,
                                                                 KikwiflowRestProperties restProperties,
                                                                 HttpIdentityResolver identityResolver,
                                                                 Environment environment) {
        String configuredBaseUrl = configuredBaseUrl(properties, environment);
        if (configuredBaseUrl == null && properties.isMonitorLinks()
                && ClassUtils.isPresent(MONITOR_MARKER_CLASS, getClass().getClassLoader())) {
            LOGGER.info("Links do Monitor nas respostas MCP usarão o endereço de cada requisição. Atrás de balanceador/"
                    + "proxy configure kikwiflow.mcp.public-base-url (ou server.forward-headers-strategy=framework), "
                    + "senão o agente recebe links com o host interno.");
        }
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint(endpointPath(properties, restProperties))
                .contextExtractor(request -> transportContext(request, identityResolver, configuredBaseUrl))
                .securityValidator(originValidator(properties, restProperties))
                .build();
    }

    @Bean
    @ConditionalOnBean({QueryRepository.class, ProcessInstanceSnapshotService.class})
    public McpStatelessSyncServer kikwiMcpServer(HttpServletStatelessServerTransport transport,
                                                 KikwiMcpProperties properties,
                                                 QueryRepository queryRepository,
                                                 ProcessInstanceSnapshotService snapshotService,
                                                 VariableSecurityPolicyManager variableSecurityPolicyManager,
                                                 ConfirmationTokenService confirmationTokens,
                                                 ObjectProvider<KikwiflowEngine> engine,
                                                 ObjectProvider<ObjectMapper> objectMapper) {
        boolean writeTools = properties.getWriteTools().isEnabled();
        KikwiflowEngine kikwiflowEngine = engine.getIfAvailable();
        if (writeTools && kikwiflowEngine == null) {
            throw new IllegalStateException("kikwiflow.mcp.write-tools.enabled=true exige um KikwiflowEngine no contexto.");
        }
        boolean monitorPresent = properties.isMonitorLinks()
                && ClassUtils.isPresent(MONITOR_MARKER_CLASS, getClass().getClassLoader());

        List<KikwiTool> tools = KikwiMcpCatalog.tools(kikwiflowEngine, queryRepository, snapshotService,
                variableSecurityPolicyManager, confirmationTokens,
                new KikwiMcpCatalog.Options(writeTools, properties.getWriteTools().getMaxBatchRetry(),
                        properties.getVariables().getMaxValueChars(), monitorPresent ? MONITOR_PATH : null),
                Clock.systemUTC());

        ObjectMapper mapper = objectMapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
        KikwiMcpServerFactory factory = new KikwiMcpServerFactory(mapper, properties.getWriteTools().isAllowAnonymous());
        String version = KikwiMcpServerFactory.class.getPackage().getImplementationVersion();

        LOGGER.info("Kikwiflow MCP server ativo: {} ferramentas ({}).", tools.size(),
                writeTools ? "leitura e escrita" : "somente leitura");
        return factory.build(transport, version != null ? version : "dev", KikwiMcpCatalog.INSTRUCTIONS, tools,
                new KikwiMcpResources(queryRepository, mapper));
    }

    @Bean
    @ConditionalOnBean(McpStatelessSyncServer.class)
    public ServletRegistrationBean<HttpServletStatelessServerTransport> kikwiMcpServletRegistration(
            HttpServletStatelessServerTransport transport, KikwiMcpProperties properties, KikwiflowRestProperties restProperties) {
        String path = endpointPath(properties, restProperties);
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration = new ServletRegistrationBean<>(transport, path);
        registration.setName("kikwiflowMcp");
        registration.setAsyncSupported(true);
        LOGGER.info("Kikwiflow MCP endpoint: {}", path);
        return registration;
    }

    static String endpointPath(KikwiMcpProperties properties, KikwiflowRestProperties restProperties) {
        if (properties.getPath() != null && !properties.getPath().isBlank()) {
            return properties.getPath();
        }
        String basePath = restProperties.getBasePath() == null ? "" : restProperties.getBasePath();
        return (basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath) + "/mcp";
    }

    static McpTransportContext transportContext(HttpServletRequest request, HttpIdentityResolver identityResolver,
                                                String configuredBaseUrl) {
        Map<String, Object> values = new HashMap<>();
        values.put(KikwiMcpServerFactory.IDENTITY_KEY, identityResolver.resolve(request));
        values.put(KikwiMcpServerFactory.BASE_URL_KEY, baseUrl(request, configuredBaseUrl));
        return McpTransportContext.create(values);
    }

    /**
     * Base pública dos links do Monitor, resolvida uma vez na subida: {@code kikwiflow.mcp.public-base-url} e, na
     * falta dela, {@code kikwiflow.monitor-ui.api-url} quando absoluta (o Monitor embutido é servido pelo mesmo host
     * da API). {@code null} = derivar de cada requisição.
     */
    static String configuredBaseUrl(KikwiMcpProperties properties, Environment environment) {
        String publicBaseUrl = properties.getPublicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return trimTrailingSlash(publicBaseUrl.trim());
        }
        String apiUrl = environment.getProperty(MONITOR_API_URL_PROPERTY);
        if (apiUrl != null && (apiUrl.startsWith("https://") || apiUrl.startsWith("http://"))) {
            return trimTrailingSlash(apiUrl.trim());
        }
        return null;
    }

    static String baseUrl(HttpServletRequest request, String configuredBaseUrl) {
        if (configuredBaseUrl != null) {
            return configuredBaseUrl;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }

    static ServerTransportSecurityValidator originValidator(KikwiMcpProperties properties, KikwiflowRestProperties restProperties) {
        Set<String> allowed = new LinkedHashSet<>(properties.getAllowedOrigins());
        if (restProperties.getCors() != null && restProperties.getCors().getAllowedOrigins() != null) {
            allowed.addAll(List.of(restProperties.getCors().getAllowedOrigins()));
        }
        return headers -> {
            List<String> origins = headerValues(headers, "origin");
            for (String origin : origins) {
                if (!allowed.contains("*") && !allowed.contains(origin)) {
                    throw new ServerTransportSecurityException(403, "Origin não permitido no servidor MCP: " + origin
                            + ". Adicione-o em kikwiflow.mcp.allowed-origins.");
                }
            }
        };
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static List<String> headerValues(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue() != null ? entry.getValue() : List.of();
            }
        }
        return List.of();
    }
}
