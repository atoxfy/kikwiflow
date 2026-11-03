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
package io.kikwiflow.connector.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.execution.ProcessVariable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Dado o conector kikwiHttp com corpo em template e autenticação client credentials")
class HttpTaskHandlerTemplateAndAuthTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> apiCalls = new ArrayList<>();
    private final List<String> tokenCalls = new ArrayList<>();
    private final List<String> loginCalls = new ArrayList<>();
    private final AtomicInteger tokenCounter = new AtomicInteger();
    private int unauthorizedResponses;

    private HttpTaskHandler handler;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenCalls.add(read(exchange));
            respond(exchange, 200, "{\"access_token\":\"tk-" + tokenCounter.incrementAndGet() + "\",\"expires_in\":300}");
        });
        server.createContext("/login", exchange -> {
            loginCalls.add(read(exchange));
            respond(exchange, 200, "{\"token\":\"lg-" + loginCalls.size() + "\",\"tipo\":\"Bearer\"}");
        });
        server.createContext("/api", exchange -> {
            apiCalls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " auth="
                    + exchange.getRequestHeaders().getFirst("Authorization") + " body=" + read(exchange));
            if (unauthorizedResponses > 0) {
                unauthorizedResponses--;
                respond(exchange, 401, "{}");
                return;
            }
            respond(exchange, 200, "{\"id\":\"deal-7\"}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        handler = new HttpTaskHandler(HttpClient.newHttpClient(), new ObjectMapper(), template -> template
                .replace("${crm.base}", baseUrl)
                .replace("${crm.client-secret}", "s3gr3do")
                .replace("${crm.empresa}", "empresa-1"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static FlowNodeDefinition node(Map<String, String> props, String... outputs) {
        return ExecutableTaskDefinition.builder()
                .id("CRIAR_LEAD").name("Criar lead").executor(HttpTaskHandler.BEAN_NAME)
                .extensionProperties(props)
                .variableBindings(new VariableBindings(null,
                        Arrays.stream(outputs).map(v -> new VariableBinding(v, true)).toList()))
                .build();
    }

    private static HttpTaskHandlerTest.FakeContext context(FlowNodeDefinition node, Object... keyValues) {
        HttpTaskHandlerTest.FakeContext context = new HttpTaskHandlerTest.FakeContext(node);
        for (int i = 0; i < keyValues.length; i += 2) {
            context.setVariable(new ProcessVariable((String) keyValues[i], keyValues[i + 1]));
        }
        return context;
    }

    @Nested
    @DisplayName("Quando o corpo vem de kikwi:http.body.template")
    class Template {

        @Test
        @DisplayName("então placeholders exatos levam o valor com tipo, os demais viram texto e ${...} vem do host")
        void rendersTypedValues() {
            Map<String, String> props = new HashMap<>();
            props.put(HttpTaskHandler.URL, "${crm.base}/api/deals");
            props.put(HttpTaskHandler.BODY_TEMPLATE, """
                    {"businessKey": "{{conversa}}", "title": "Lead {{nome}}", "empresa": "${crm.empresa}",
                     "variables": [{"name": "contato.nome", "value": "{{nome}}"},
                                   {"name": "veiculo.ano", "value": "{{ano}}"},
                                   {"name": "veiculo.placa", "value": "{{placa}}"}]}""");

            handler.handle(context(node(props), "conversa", "c-1", "nome", "Maria", "ano", 2019));

            assertEquals("POST /api/deals auth=null body={\"businessKey\":\"c-1\",\"title\":\"Lead Maria\","
                    + "\"empresa\":\"empresa-1\",\"variables\":[{\"name\":\"contato.nome\",\"value\":\"Maria\"},"
                    + "{\"name\":\"veiculo.ano\",\"value\":2019},{\"name\":\"veiculo.placa\",\"value\":null}]}",
                    apiCalls.getFirst());
        }

        @Test
        @DisplayName("com omit-empty, então itens de lista sem valor não vão e campos vazios somem")
        void omitsEmpty() {
            Map<String, String> props = new HashMap<>();
            props.put(HttpTaskHandler.URL, "${crm.base}/api/deals/{{dealId}}");
            props.put(HttpTaskHandler.METHOD, "PUT");
            props.put(HttpTaskHandler.BODY_OMIT_EMPTY, "true");
            props.put(HttpTaskHandler.BODY_TEMPLATE, """
                    {"destinationId": "{{destino}}", "nota": "{{nota}}",
                     "variables": [{"name": "veiculo.modelo", "value": "{{modelo}}"},
                                   {"name": "veiculo.placa", "value": "{{placa}}"}]}""");

            handler.handle(context(node(props), "dealId", "d-1", "modelo", "Onix", "placa", " "));

            assertEquals("PUT /api/deals/d-1 auth=null body={\"variables\":[{\"name\":\"veiculo.modelo\","
                    + "\"value\":\"Onix\"}]}", apiCalls.getFirst());
        }
    }

    @Nested
    @DisplayName("Quando kikwi:http.auth=client-credentials")
    class ClientCredentials {

        private Map<String, String> props() {
            Map<String, String> props = new HashMap<>();
            props.put(HttpTaskHandler.URL, "${crm.base}/api/deals");
            props.put(HttpTaskHandler.AUTH, HttpTaskHandler.CLIENT_CREDENTIALS);
            props.put(HttpTaskHandler.AUTH_TOKEN_URL, "${crm.base}/token");
            props.put(HttpTaskHandler.AUTH_CLIENT_ID, "kikwi-agents");
            props.put(HttpTaskHandler.AUTH_CLIENT_SECRET, "${crm.client-secret}");
            props.put(HttpTaskHandler.RESPONSE_PREFIX + "dealId", "/id");
            return props;
        }

        @Test
        @DisplayName("então obtém o token uma vez, reaproveita nas chamadas seguintes e grava o output")
        void reusesToken() {
            HttpTaskHandlerTest.FakeContext first = context(node(props(), "dealId"));
            handler.handle(first);
            handler.handle(context(node(props(), "dealId")));

            assertEquals(List.of("grant_type=client_credentials&client_id=kikwi-agents&client_secret=s3gr3do"), tokenCalls);
            assertEquals("POST /api/deals auth=Bearer tk-1 body={}", apiCalls.get(0));
            assertEquals("POST /api/deals auth=Bearer tk-1 body={}", apiCalls.get(1));
            assertEquals("deal-7", first.getVariable("dealId").value());
        }

        @Test
        @DisplayName("e a API responde 401, então descarta o token, obtém outro e tenta uma vez de novo")
        void renewsOn401() {
            handler.handle(context(node(props())));
            unauthorizedResponses = 1;

            handler.handle(context(node(props())));

            assertEquals(2, tokenCalls.size());
            assertEquals("POST /api/deals auth=Bearer tk-1 body={}", apiCalls.get(1));
            assertEquals("POST /api/deals auth=Bearer tk-2 body={}", apiCalls.get(2));
        }

        @Test
        @DisplayName("com kikwi:http.auth=password, então obtém o token do usuário técnico (client público, sem secret)")
        void passwordGrant() {
            Map<String, String> props = props();
            props.put(HttpTaskHandler.AUTH, HttpTaskHandler.PASSWORD);
            props.remove(HttpTaskHandler.AUTH_CLIENT_SECRET);
            props.put(HttpTaskHandler.AUTH_USERNAME, "agente-ia@loja.com");
            props.put(HttpTaskHandler.AUTH_PASSWORD, "${crm.client-secret}");

            handler.handle(context(node(props)));

            assertEquals(List.of("grant_type=password&client_id=kikwi-agents&username=agente-ia%40loja.com"
                    + "&password=s3gr3do"), tokenCalls);
            assertEquals("POST /api/deals auth=Bearer tk-1 body={}", apiCalls.getFirst());
        }

        @Test
        @DisplayName("com kikwi:http.auth=login, então faz o login JSON da API, lê o token do campo e reaproveita")
        void loginEndpoint() {
            Map<String, String> props = props();
            props.keySet().removeIf(k -> k.startsWith(HttpTaskHandler.PREFIX + "auth."));
            props.put(HttpTaskHandler.AUTH, HttpTaskHandler.LOGIN);
            props.put(HttpTaskHandler.AUTH_LOGIN_URL, "${crm.base}/login");
            props.put(HttpTaskHandler.AUTH_LOGIN_BODY,
                    "{\"email\": \"agente-ia@loja.com\", \"senha\": \"${crm.client-secret}\"}");

            handler.handle(context(node(props)));
            handler.handle(context(node(props)));

            assertEquals(List.of("{\"email\": \"agente-ia@loja.com\", \"senha\": \"s3gr3do\"}"), loginCalls);
            assertEquals("POST /api/deals auth=Bearer lg-1 body={}", apiCalls.get(0));
            assertEquals("POST /api/deals auth=Bearer lg-1 body={}", apiCalls.get(1));
        }

        @Test
        @DisplayName("com kikwi:http.auth=login e 401 da API, então faz login de novo e tenta uma vez")
        void loginRenewsOn401() {
            Map<String, String> props = props();
            props.put(HttpTaskHandler.AUTH, HttpTaskHandler.LOGIN);
            props.put(HttpTaskHandler.AUTH_LOGIN_URL, "${crm.base}/login");
            props.put(HttpTaskHandler.AUTH_LOGIN_BODY, "{\"senha\": \"${crm.client-secret}\"}");
            unauthorizedResponses = 1;

            handler.handle(context(node(props)));

            assertEquals(2, loginCalls.size());
            assertEquals("POST /api/deals auth=Bearer lg-2 body={}", apiCalls.get(1));
        }

        @Test
        @DisplayName("sem client-secret configurado, então é erro de configuração do nó")
        void requiresSecret() {
            Map<String, String> props = props();
            props.remove(HttpTaskHandler.AUTH_CLIENT_SECRET);
            assertThrows(IllegalArgumentException.class, () -> handler.handle(context(node(props))));
        }
    }
}
