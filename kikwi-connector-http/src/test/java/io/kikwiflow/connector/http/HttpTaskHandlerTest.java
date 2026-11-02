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
import com.sun.net.httpserver.HttpServer;
import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.execution.ProcessVariable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Dado um EXECUTABLE_TASK que usa o conector kikwiHttp")
class HttpTaskHandlerTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private int status = 200;
    private String responseBody = "{}";

    private final HttpTaskHandler handler = new HttpTaskHandler(HttpClient.newHttpClient(), new ObjectMapper(),
            template -> template.replace("${crm.token}", "segredo-do-host").replace("${crm.base}", baseUrl));

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastRequest.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " auth="
                    + exchange.getRequestHeaders().getFirst("Authorization") + " body=" + body);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static FlowNodeDefinition node(Map<String, String> props, String... outputs) {
        return ExecutableTaskDefinition.builder()
                .id("AGENDAR").name("Agendar").executor(HttpTaskHandler.BEAN_NAME)
                .extensionProperties(props)
                .variableBindings(new VariableBindings(null,
                        java.util.Arrays.stream(outputs).map(v -> new VariableBinding(v, true)).toList()))
                .build();
    }

    private static FakeContext context(FlowNodeDefinition node, Object... keyValues) {
        FakeContext context = new FakeContext(node);
        for (int i = 0; i < keyValues.length; i += 2) {
            context.setVariable(new ProcessVariable((String) keyValues[i], keyValues[i + 1]));
        }
        return context;
    }

    @Test
    @DisplayName("Quando a API responde 2xx, então envia o corpo, resolve URL e cabeçalhos e grava os outputs")
    void callsAndMapsOutputs() {
        responseBody = "{\"data\":{\"id\":\"AG-9\"},\"dataHora\":\"2026-11-03T10:00:00Z\"}";
        FakeContext context = context(node(Map.of(
                        HttpTaskHandler.URL, "${crm.base}/leads/{{leadId}}/agendamento",
                        HttpTaskHandler.HEADER_PREFIX + "Authorization", "Bearer ${crm.token}",
                        HttpTaskHandler.BODY, "dataHora, loja",
                        HttpTaskHandler.RESPONSE_PREFIX + "agendamentoId", "/data/id"),
                        "agendamentoId", "dataHora"),
                "leadId", "lead 1", "dataHora", "2026-11-03T10:00:00Z", "loja", "Centro", "interno", "não vai");

        handler.handle(context);

        assertEquals("POST /leads/lead%201/agendamento auth=Bearer segredo-do-host "
                + "body={\"dataHora\":\"2026-11-03T10:00:00Z\",\"loja\":\"Centro\"}", lastRequest.get());
        assertEquals("AG-9", context.getVariable("agendamentoId").value());
        assertEquals("2026-11-03T10:00:00Z", context.getVariable("dataHora").value());
    }

    @Test
    @DisplayName("Quando a API responde 4xx, então vira erro de negócio HTTP_<status> para um error boundary tratar")
    void clientErrorIsBusinessError() {
        status = 409;
        responseBody = "{\"erro\":\"horário ocupado\"}";
        ProcessErrorException error = assertThrows(ProcessErrorException.class, () -> handler.handle(
                context(node(Map.of(HttpTaskHandler.URL, baseUrl + "/agenda")))));
        assertEquals("HTTP_409", error.getErrorCode());
        assertTrue(error.getMessage().contains("horário ocupado"));
    }

    @Test
    @DisplayName("Quando a API responde 5xx, então é falha técnica (retry), não erro de negócio")
    void serverErrorIsTechnicalFailure() {
        status = 503;
        RuntimeException error = assertThrows(RuntimeException.class, () -> handler.handle(
                context(node(Map.of(HttpTaskHandler.URL, baseUrl + "/agenda")))));
        assertFalse(error instanceof ProcessErrorException);
    }

    @Test
    @DisplayName("Quando o método é GET, então não envia corpo")
    void getSendsNoBody() {
        handler.handle(context(node(Map.of(HttpTaskHandler.METHOD, "get",
                HttpTaskHandler.URL, baseUrl + "/leads?tel={{telefone}}")), "telefone", "+55 51 9"));
        assertEquals("GET /leads?tel=%2B55%2051%209 auth=null body=", lastRequest.get());
    }

    private static final class FakeContext implements ExecutionContext {
        private final FlowNodeDefinition node;
        private final Map<String, ProcessVariable> variables = new HashMap<>();

        FakeContext(FlowNodeDefinition node) {
            this.node = node;
        }

        @Override
        public void setVariable(String variableName, ProcessVariable value) {
            variables.put(variableName, value);
        }

        @Override
        public void removeVariable(String variableName) {
            variables.remove(variableName);
        }

        @Override
        public ProcessVariable getVariable(String variableName) {
            return variables.get(variableName);
        }

        @Override
        public boolean hasVariable(String variableName) {
            return variables.containsKey(variableName);
        }

        @Override
        public String getProcessInstanceId() {
            return "pi-1";
        }

        @Override
        public FlowNodeDefinition getFlowNode() {
            return node;
        }
    }
}
