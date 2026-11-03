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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens para o {@link HttpTaskHandler}: OAuth2 por <i>client credentials</i> (a aplicação) ou <i>password</i> (um
 * usuário técnico, para APIs que exigem um usuário de verdade), ou o <i>login</i> próprio de uma API (POST JSON que
 * devolve o token num campo). Um por credencial, reaproveitado até {@value #SAFETY_SECONDS}s antes de expirar. Falha ao
 * obter o token é técnica (o motor faz retry).
 */
class OAuth2Tokens {

    static final long SAFETY_SECONDS = 30;
    private static final long DEFAULT_EXPIRES_IN = 60;

    enum Grant {
        CLIENT_CREDENTIALS("client_credentials"),
        PASSWORD("password"),
        /** Endpoint de login da própria API (não é OAuth2): corpo JSON, token num JSON Pointer da resposta. */
        LOGIN("login");

        private final String value;

        Grant(String value) {
            this.value = value;
        }
    }

    /**
     * @param clientSecret opcional no grant password (client público).
     * @param username     e {@code password}: só no grant password.
     * @param scope        opcional.
     * @param loginBody    e {@code tokenPointer}, {@code ttl}: só no login (o corpo já com os segredos do host; o TTL
     *                     vale quando a resposta não traz {@code expires_in}).
     */
    record Client(Grant grant, String tokenUrl, String clientId, String clientSecret, String username,
                  String password, String scope, String loginBody, String tokenPointer, Duration ttl) {

        Client(Grant grant, String tokenUrl, String clientId, String clientSecret, String username, String password,
               String scope) {
            this(grant, tokenUrl, clientId, clientSecret, username, password, scope, null, null, null);
        }

        static Client login(String url, String body, String tokenPointer, Duration ttl) {
            return new Client(Grant.LOGIN, url, null, null, null, null, null, body, tokenPointer, ttl);
        }

        String key() {
            return grant + "|" + tokenUrl + "|" + clientId + "|" + (username == null ? "" : username)
                    + "|" + (loginBody == null ? "" : loginBody);
        }

        /** Para mensagens de erro: nunca o corpo do login, que tem a senha. */
        String describe() {
            return grant == Grant.LOGIN ? "login" : "client '" + clientId + "'"
                    + (username == null ? "" : " and user '" + username + "'");
        }
    }

    private record Token(String value, Instant expiresAt) {
    }

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Map<String, Token> cache = new ConcurrentHashMap<>();

    OAuth2Tokens(HttpClient http, ObjectMapper mapper) {
        this(http, mapper, Clock.systemUTC());
    }

    OAuth2Tokens(HttpClient http, ObjectMapper mapper, Clock clock) {
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
    }

    String token(Client client) {
        Token cached = cache.get(client.key());
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
            return cached.value();
        }
        Token fresh = request(client);
        cache.put(client.key(), fresh);
        return fresh.value();
    }

    void invalidate(Client client) {
        cache.remove(client.key());
    }

    private Token request(Client client) {
        if (client.grant() == Grant.LOGIN) {
            return parse(client, post(client, "application/json", client.loginBody()),
                    client.tokenPointer(), client.ttl());
        }
        StringBuilder form = new StringBuilder("grant_type=").append(client.grant().value)
                .append("&client_id=").append(encode(client.clientId()));
        if (client.clientSecret() != null) {
            form.append("&client_secret=").append(encode(client.clientSecret()));
        }
        if (client.grant() == Grant.PASSWORD) {
            form.append("&username=").append(encode(client.username()))
                    .append("&password=").append(encode(client.password()));
        }
        if (client.scope() != null) {
            form.append("&scope=").append(encode(client.scope()));
        }
        return parse(client, post(client, "application/x-www-form-urlencoded", form.toString()), "/access_token",
                Duration.ofSeconds(DEFAULT_EXPIRES_IN));
    }

    private HttpResponse<String> post(Client client, String contentType, String body) {
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(client.tokenUrl()))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", contentType)
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not obtain a token from " + client.tokenUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while obtaining a token", e);
        }
        if (response.statusCode() / 100 != 2) {
            // O corpo pode trazer detalhes do client; não ecoa segredos (o pedido é que os tem, não a resposta).
            throw new IllegalStateException("Token endpoint " + client.tokenUrl() + " answered HTTP "
                    + response.statusCode() + " for " + client.describe());
        }
        return response;
    }

    private Token parse(Client client, HttpResponse<String> response, String tokenPointer, Duration ttl) {
        try {
            JsonNode json = mapper.readTree(response.body());
            String token = json.at(tokenPointer).asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Token endpoint " + client.tokenUrl() + " returned no token at "
                        + tokenPointer);
            }
            long expiresIn = json.path("expires_in").asLong(ttl.toSeconds());
            return new Token(token, clock.instant().plusSeconds(Math.max(0, expiresIn - SAFETY_SECONDS)));
        } catch (IOException e) {
            throw new UncheckedIOException("Token response is not JSON", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
