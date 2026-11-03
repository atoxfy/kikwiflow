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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Iterator;
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
 *   <li>{@code kikwi:http.body.template} — corpo JSON livre, para APIs com outro formato (ex.: lista
 *   {@code [{"name": ..., "value": ...}]}). Um valor que é só {@code "{{variavel}}"} recebe o valor da instância com o
 *   tipo dele (texto, número, booleano, lista); dentro de um texto maior, vira texto. {@code ${...}} vem do host. Tem
 *   precedência sobre {@code kikwi:http.body}. Com {@code kikwi:http.body.omit-empty=true}, campos sem valor somem e
 *   itens de lista que ficariam com algum campo sem valor não são enviados (um item incompleto não vai);</li>
 *   <li>{@code kikwi:http.response.<variavel>} — JSON Pointer na resposta (padrão {@code /<variavel>}) para cada
 *   variável dos {@code outputs} do nó;</li>
 *   <li>{@code kikwi:http.timeout} — duração ISO-8601 (padrão PT30S);</li>
 *   <li>{@code kikwi:http.auth=client-credentials} — token OAuth2 (client credentials) obtido em
 *   {@code kikwi:http.auth.token-url} com {@code kikwi:http.auth.client-id}/{@code client-secret} (e
 *   {@code scope}, opcional), todos com {@code ${...}} do host. O token é reaproveitado até perto de expirar; um 401
 *   da API descarta o token e tenta uma vez de novo;</li>
 *   <li>{@code kikwi:http.auth=password} — o mesmo, com o token de um usuário técnico ({@code kikwi:http.auth.username}
 *   e {@code password}, sempre de {@code ${...}}; {@code client-secret} opcional), para APIs que exigem um usuário de
 *   verdade (permissões, auditoria, "responsável").</li>
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
    static final String BODY_TEMPLATE = PREFIX + "body.template";
    static final String BODY_OMIT_EMPTY = PREFIX + "body.omit-empty";
    static final String AUTH = PREFIX + "auth";
    static final String AUTH_TOKEN_URL = PREFIX + "auth.token-url";
    static final String AUTH_CLIENT_ID = PREFIX + "auth.client-id";
    static final String AUTH_CLIENT_SECRET = PREFIX + "auth.client-secret";
    static final String AUTH_SCOPE = PREFIX + "auth.scope";
    static final String AUTH_USERNAME = PREFIX + "auth.username";
    static final String AUTH_PASSWORD = PREFIX + "auth.password";
    static final String CLIENT_CREDENTIALS = "client-credentials";
    static final String PASSWORD = "password";

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*}}");
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_ERROR_BODY = 500;
    /** Marca interna de objeto que perdeu um campo por falta de valor (omit-empty); nunca é enviada. */
    private static final String INCOMPLETE = "__kikwiIncomplete";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final UnaryOperator<String> propertyResolver;
    private final OAuth2Tokens tokens;

    /**
     * @param propertyResolver resolve {@code ${...}} com a configuração do host (no Spring, o {@code Environment}).
     */
    public HttpTaskHandler(HttpClient client, ObjectMapper mapper, UnaryOperator<String> propertyResolver) {
        this.client = Objects.requireNonNull(client);
        this.mapper = Objects.requireNonNull(mapper);
        this.propertyResolver = propertyResolver == null ? UnaryOperator.identity() : propertyResolver;
        this.tokens = new OAuth2Tokens(client, mapper);
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
            String body = props.containsKey(BODY_TEMPLATE)
                    ? templateBody(props.get(BODY_TEMPLATE), Boolean.parseBoolean(props.get(BODY_OMIT_EMPTY)), execution)
                    : body(props.get(BODY), execution);
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }

        OAuth2Tokens.Client credentials = credentials(node, props);
        HttpResponse<String> response = send(node, request, credentials, false);
        if (credentials != null && response.statusCode() == 401) {
            tokens.invalidate(credentials);
            response = send(node, request, credentials, true);
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

    private HttpResponse<String> send(FlowNodeDefinition node, HttpRequest.Builder request,
                                      OAuth2Tokens.Client credentials, boolean retry) {
        try {
            if (credentials != null) {
                request.setHeader("Authorization", "Bearer " + tokens.token(credentials));
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("HTTP call failed for node '" + node.id() + "'"
                    + (retry ? " (after renewing the token)" : ""), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call interrupted for node '" + node.id() + "'", e);
        }
    }

    private OAuth2Tokens.Client credentials(FlowNodeDefinition node, Map<String, String> props) {
        String auth = props.get(AUTH);
        if (auth == null || auth.isBlank()) {
            return null;
        }
        boolean password = PASSWORD.equals(auth.trim());
        if (!password && !CLIENT_CREDENTIALS.equals(auth.trim())) {
            throw new IllegalArgumentException("Node '" + node.id() + "': unsupported " + AUTH + " '" + auth + "'.");
        }
        return new OAuth2Tokens.Client(password ? OAuth2Tokens.Grant.PASSWORD : OAuth2Tokens.Grant.CLIENT_CREDENTIALS,
                required(node, props, AUTH_TOKEN_URL), required(node, props, AUTH_CLIENT_ID),
                password ? optional(props, AUTH_CLIENT_SECRET) : required(node, props, AUTH_CLIENT_SECRET),
                password ? required(node, props, AUTH_USERNAME) : null,
                password ? required(node, props, AUTH_PASSWORD) : null,
                optional(props, AUTH_SCOPE));
    }

    private String optional(Map<String, String> props, String key) {
        String value = props.get(key);
        return value == null || value.isBlank() ? null : propertyResolver.apply(value);
    }

    private String required(FlowNodeDefinition node, Map<String, String> props, String key) {
        String value = props.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Node '" + node.id() + "': " + key + " is required.");
        }
        return propertyResolver.apply(value);
    }

    /** Corpo livre: ver {@code kikwi:http.body.template} na documentação da classe. */
    private String templateBody(String template, boolean omitEmpty, ExecutionContext execution) {
        JsonNode json;
        try {
            json = mapper.readTree(propertyResolver.apply(template));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(BODY_TEMPLATE + " is not valid JSON", e);
        }
        JsonNode rendered = renderJson(json, execution, omitEmpty);
        if (rendered != null) {
            stripIncompleteMarks(rendered);
        }
        try {
            return mapper.writeValueAsString(rendered == null ? mapper.nullNode() : rendered);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize the request body", e);
        }
    }

    /** @return o nó com os placeholders resolvidos; {@code null} = sem valor (só com omit-empty). */
    private JsonNode renderJson(JsonNode node, ExecutionContext execution, boolean omitEmpty) {
        if (node.isTextual()) {
            Matcher exact = VARIABLE.matcher(node.asText());
            if (exact.matches()) {
                ProcessVariable variable = execution.getVariable(exact.group(1));
                Object value = variable == null ? null : variable.value();
                if (value == null || (value instanceof String text && text.isBlank())) {
                    return omitEmpty ? null : mapper.nullNode();
                }
                return mapper.valueToTree(value);
            }
            return mapper.getNodeFactory().textNode(render(node.asText(), execution, false));
        }
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = renderJson(field.getValue(), execution, omitEmpty);
                if (value != null) {
                    result.set(field.getKey(), value);
                } else {
                    result.put(INCOMPLETE, true);
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            for (JsonNode item : node) {
                JsonNode value = renderJson(item, execution, omitEmpty);
                if (value != null && !(value.isObject() && value.has(INCOMPLETE))) {
                    result.add(value);
                }
            }
            return result;
        }
        return node;
    }

    /** Fora de listas, um objeto incompleto continua (só sem os campos vazios): a marca interna sai. */
    private static void stripIncompleteMarks(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove(INCOMPLETE);
        }
        node.forEach(HttpTaskHandler::stripIncompleteMarks);
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
