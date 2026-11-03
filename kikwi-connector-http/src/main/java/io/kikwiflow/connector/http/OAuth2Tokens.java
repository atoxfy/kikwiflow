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
 * Tokens OAuth2 para o {@link HttpTaskHandler}, por <i>client credentials</i> (a aplicação) ou <i>password</i> (um
 * usuário técnico, para APIs que exigem um usuário de verdade): um por (token URL, client id, usuário), reaproveitado
 * até {@value #SAFETY_SECONDS}s antes de expirar. Falha ao obter o token é técnica (o motor faz retry).
 */
class OAuth2Tokens {

    static final long SAFETY_SECONDS = 30;
    private static final long DEFAULT_EXPIRES_IN = 60;

    enum Grant {
        CLIENT_CREDENTIALS("client_credentials"),
        PASSWORD("password");

        private final String value;

        Grant(String value) {
            this.value = value;
        }
    }

    /**
     * @param clientSecret opcional no grant password (client público).
     * @param username     e {@code password}: só no grant password.
     * @param scope        opcional.
     */
    record Client(Grant grant, String tokenUrl, String clientId, String clientSecret, String username,
                  String password, String scope) {

        String key() {
            return grant + "|" + tokenUrl + "|" + clientId + "|" + (username == null ? "" : username);
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
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(client.tokenUrl()))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not obtain an OAuth2 token from " + client.tokenUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while obtaining an OAuth2 token", e);
        }
        if (response.statusCode() / 100 != 2) {
            // O corpo pode trazer detalhes do client; não ecoa segredos (o pedido é que os tem, não a resposta).
            throw new IllegalStateException("OAuth2 token endpoint " + client.tokenUrl() + " answered HTTP "
                    + response.statusCode() + " for client '" + client.clientId() + "'"
                    + (client.username() == null ? "" : " and user '" + client.username() + "'"));
        }
        try {
            JsonNode json = mapper.readTree(response.body());
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("OAuth2 token endpoint " + client.tokenUrl() + " returned no access_token");
            }
            long expiresIn = json.path("expires_in").asLong(DEFAULT_EXPIRES_IN);
            return new Token(token, clock.instant().plusSeconds(Math.max(0, expiresIn - SAFETY_SECONDS)));
        } catch (IOException e) {
            throw new UncheckedIOException("OAuth2 token response is not JSON", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
