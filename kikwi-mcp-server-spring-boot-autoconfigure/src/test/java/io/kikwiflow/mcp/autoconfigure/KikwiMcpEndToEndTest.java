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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.DefaultVariableSecurityPolicyManager;
import io.kikwiflow.security.HttpIdentityResolver;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ponta a ponta pelo transporte real: Spring Boot numa porta aleatória, autoconfiguração MCP, identidade pelo
 * {@link HttpIdentityResolver} do host e o cliente oficial do SDK falando Streamable HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "kikwiflow.mcp.enabled=true",
        "kikwiflow.mcp.write-tools.enabled=true",
        "kikwiflow.mcp.confirmation.secret=um-segredo-de-teste-com-mais-de-32-caracteres"
})
@DisplayName("Servidor MCP via HTTP")
class KikwiMcpEndToEndTest {

    private static final String ENDPOINT = "/kikwiflow/api/v1/mcp";

    @LocalServerPort
    int port;

    // Beans comuns (não @MockitoBean): as condições @ConditionalOnBean da autoconfiguração precisam enxergá-los.
    @Autowired
    QueryRepository queryRepository;

    @Autowired
    KikwiflowEngine engine;

    private McpSyncClient client;

    @AfterEach
    void close() {
        if (client != null) {
            client.closeGracefully();
        }
        reset(queryRepository, engine);
    }

    @Test
    @DisplayName("Lista ferramentas em ordem determinística, com instruções, recursos e prompts")
    void listsCapabilities() {
        client = connect("ana");

        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
        assertEquals(names.stream().sorted().toList(), names);
        assertTrue(names.containsAll(List.of("diagnose_incident", "retry_incidents", "start_process_instance")));
        McpSchema.Tool retry = client.listTools().tools().stream().filter(t -> t.name().equals("retry_incidents")).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, retry.annotations().destructiveHint());
        assertTrue(client.getServerInstructions().contains("Kikwiflow"));
        assertEquals(2, client.listResourceTemplates().resourceTemplates().size());
        assertTrue(client.listPrompts().prompts().stream().anyMatch(p -> p.name().equals("troubleshoot-incident")));
    }

    @Test
    @DisplayName("Lê a definição implantada como .kikwi pelo recurso kikwi://process-definitions/{key}")
    void readsDefinitionResource() {
        Map<String, FlowNodeDefinition> nodes = new LinkedHashMap<>();
        nodes.put("inicio", StartEventDefinition.builder().id("inicio").name("Início").build());
        ProcessDefinition definition = ProcessDefinition.builder().id("def-1").key("kyc").name("KYC").version(2)
                .checksum("abc").defaultStartPoint("inicio").flowNodes(nodes).build();
        when(queryRepository.findLatestVersionByKey("kyc")).thenReturn(Optional.of(definition));
        client = connect("ana");

        McpSchema.ReadResourceResult result = client.readResource(new McpSchema.ReadResourceRequest("kikwi://process-definitions/kyc"));

        String json = ((McpSchema.TextResourceContents) result.contents().get(0)).text();
        assertTrue(json.contains("\"key\" : \"kyc\""), json);
        assertFalse(json.contains("checksum"), json);
    }

    @Test
    @DisplayName("Retry em duas etapas pelo HTTP: preview com token e execução em nome do usuário do header")
    void retryWithConfirmation() {
        when(queryRepository.findIncidentById("i-1")).thenReturn(Optional.of(new Incident("i-1", "FAILED_TASK", "Read timed out",
                null, "def-1", "pi-1", "et-1", Instant.now(), IncidentStatus.OPEN, "analisar")));
        client = connect("ana");

        McpSchema.CallToolResult preview = client.callTool(new McpSchema.CallToolRequest("retry_incidents",
                Map.of("incidentIds", List.of("i-1"))));
        assertFalse(preview.isError(), () -> String.valueOf(preview.content()));
        @SuppressWarnings("unchecked")
        String token = (String) ((Map<String, Object>) preview.structuredContent()).get("confirmationToken");

        McpSchema.CallToolResult executed = client.callTool(new McpSchema.CallToolRequest("retry_incidents",
                Map.of("confirmationToken", token)));

        assertFalse(executed.isError(), () -> String.valueOf(executed.content()));
        verify(engine).retryIncident(eq("i-1"), argThat(identity -> "ana".equals(identity.actorId())));
    }

    @Test
    @DisplayName("Sem usuário autenticado, ferramentas de escrita respondem FORBIDDEN")
    void anonymousCannotWrite() {
        client = connect(null);

        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("retry_incidents",
                Map.of("incidentIds", List.of("i-1"))));

        assertTrue(result.isError());
        assertTrue(String.valueOf(result.content()).contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("Requisição de navegador com Origin não permitido é recusada (proteção contra DNS rebinding)")
    void rejectsForeignOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Origin", "http://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
    }

    private McpSyncClient connect(String user) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint(ENDPOINT)
                .httpRequestCustomizer((builder, method, uri, body, context) -> {
                    if (user != null) {
                        builder.header("X-User-Id", user);
                    }
                })
                .build();
        McpSyncClient syncClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        syncClient.initialize();
        return syncClient;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        /** Identidade de teste: usuário no header (só para o teste — o resolver padrão não confia em X-User-Id). */
        @Bean
        HttpIdentityResolver testIdentityResolver() {
            return request -> {
                String user = request.getHeader("X-User-Id");
                return new IdentityContext(user != null ? user : "anonymous", request.getHeader("X-Tenant-Id"), Set.of());
            };
        }

        @Bean
        QueryRepository queryRepository() {
            return mock(QueryRepository.class);
        }

        @Bean
        KikwiflowEngine kikwiflowEngine() {
            return mock(KikwiflowEngine.class);
        }

        /** Exigido pelos controllers REST de deploy, que sobem junto. */
        @Bean
        ProcessDefinitionService processDefinitionService() {
            return mock(ProcessDefinitionService.class);
        }

        @Bean
        VariableSecurityPolicyManager variableSecurityPolicyManager() {
            return new DefaultVariableSecurityPolicyManager();
        }
    }
}
