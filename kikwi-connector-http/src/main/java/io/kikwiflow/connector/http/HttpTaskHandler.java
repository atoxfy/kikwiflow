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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.exception.ProcessErrorException;
import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.execution.ProcessVariable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conector HTTP genérico (bean {@code kikwiHttp}): um {@code EXECUTABLE_TASK} chama uma API sem código, configurado
 * só pelas {@code extensionProperties} do nó. É o que permite a qualquer cliente ligar processos (inclusive os que
 * agentes usam como ferramenta) à própria API.
 *
 * <ul>
 *   <li>{@code kikwi:http.method} — GET, POST, PUT, PATCH ou DELETE (padrão POST);</li>
 *   <li>{@code kikwi:http.url} — com {@code {{variavel}}} (valor da instância, codificado para URL) e
 *   {@code ${propriedade}} (configuração do host, ex.: a URL base do cliente);</li>
 *   <li>{@code kikwi:http.header.<Nome>} — cabeçalhos, com os mesmos placeholders (segredos vêm de
 *   {@code ${...}}, nunca do processo);</li>
 *   <li>{@code kikwi:http.body} — variáveis enviadas como objeto JSON, separadas por vírgula. Ausente em
 *   POST/PUT/PATCH = {@code {}};</li>
 *   <li>{@code kikwi:http.response.<variavel>} — JSON Pointer na resposta (padrão {@code /<variavel>}) para cada
 *   variável dos {@code outputs} do nó;</li>
 *   <li>{@code kikwi:http.timeout} — duração ISO-8601 (padrão PT30S).</li>
 * </ul>
 *
 * Resposta 4xx é resultado de negócio: vira {@link ProcessErrorException} com código {@code HTTP_<status>}, que um
 * {@code BOUNDARY_ERROR_HANDLER} pode tratar (ex.: compensação). 5xx e falha de rede são técnicas: a exceção sobe e
 * o motor aplica a política de retry do nó. Os {@code outputs} do nó continuam validados pelo motor depois daqui.
 */
public class HttpTaskHandler implements TaskHandler {

    public static final String BEAN_NAME = "kikwiHttp";

    static final String PREFIX = "kikwi:http.";
    static final String METHOD = PREFIX + "method";
    static final String URL = PREFIX + "url";
    static final String HEADER_PREFIX = PREFIX + "header.";
    static final String BODY = PREFIX + "body";
    static final String RESPONSE_PREFIX = PREFIX + "response.";
    static final String TIMEOUT = PREFIX + "timeout";

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_ERROR_BODY = 500;

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final UnaryOperator<String> propertyResolver;

    /**
     * @param propertyResolver resolve {@code ${...}} com a configuração do host (no Spring, o {@code Environment}).
     */
    public HttpTaskHandler(HttpClient client, ObjectMapper mapper, UnaryOperator<String> propertyResolver) {
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper);
        this.propertyResolver = propertyResolver == null ? UnaryOperator.identity() : propertyResolver;
    }

    @Override
    public void handle(ExecutionContext execution) {
        FlowNodeDefinition node = execution.getFlowNode();
        Map<String, String> props = node.extensionProperties() == null ? Map.of() : node.extensionProperties();

        String method = props.getOrDefault(METHOD, "POST").trim().toUpperCase();
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("Node '" + node.id() + "': unsupported " + METHOD + " '" + method + "'.");
        }
        String url = props.get(URL);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Node '" + node.id() + "': " + URL + " is required.");
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(render(url, execution, true)))
                .timeout(Duration.parse(props.getOrDefault(TIMEOUT, "PT30S")))
                .header("Accept", "application/json");
        props.forEach((key, value) -> {
            if (key.startsWith(HEADER_PREFIX)) {
                request.header(key.substring(HEADER_PREFIX.length()), render(value, execution, false));
            }
        });

        boolean sendsBody = !method.equals("GET") && !method.equals("DELETE");
        if (sendsBody) {
            request.header("Content-Type", "application/json");
            request.method(method, HttpRequest.BodyPublishers.ofString(body(props.get(BODY), execution)));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response;
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP call failed for node '" + node.id() + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call interrupted for node '" + node.id() + "'", e);
        }

        int status = response.statusCode();
        if (status >= 400 && status < 500) {
            throw new ProcessErrorException("HTTP_" + status, truncate(response.body()));
        }
        if (status >= 500 || status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " from node '" + node.id() + "': " + truncate(response.body()));
        }
        mapResponse(node, props, response.body(), execution);
    }

    private String body(String bodyVariables, ExecutionContext execution) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (bodyVariables != null && !bodyVariables.isBlank()) {
            Arrays.stream(bodyVariables.split(",")).map(String::trim).filter(name -> !name.isEmpty()).forEach(name -> {
                ProcessVariable variable = execution.getVariable(name);
                if (variable != null) {
                    body.put(name, variable.value());
                }
            });
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize the request body", e);
        }
    }

    private void mapResponse(FlowNodeDefinition node, Map<String, String> props, String body, ExecutionContext execution) {
        if (!(node instanceof VariableBindingAware aware) || aware.variableBindingsOrEmpty().outputs().isEmpty()) {
            return;
        }
        JsonNode json;
        try {
            json = body == null || body.isBlank() ? mapper.missingNode() : mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Response of node '" + node.id() + "' is not JSON", e);
        }
        for (VariableBinding output : aware.variableBindingsOrEmpty().outputs()) {
            String pointer = props.getOrDefault(RESPONSE_PREFIX + output.variable(), "/" + output.variable());
            JsonNode value = json.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
                execution.setVariable(new ProcessVariable(output.variable(), mapper.convertValue(value, Object.class)));
            }
        }
    }

    /** {@code ${...}} da configuração do host primeiro, depois {@code {{variavel}}} da instância. */
    private String render(String template, ExecutionContext execution, boolean urlEncode) {
        String resolved = propertyResolver.apply(template);
        Matcher matcher = VARIABLE.matcher(resolved);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            ProcessVariable variable = execution.getVariable(matcher.group(1));
            String value = variable == null || variable.value() == null ? "" : String.valueOf(variable.value());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    urlEncode ? URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20") : value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_ERROR_BODY ? body : body.substring(0, MAX_ERROR_BODY) + "…";
    }
}
