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

import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.model.security.IdentityContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Confirmação de ações de escrita em duas etapas, sem estado no servidor: a etapa de preview devolve um token
 * HMAC-SHA256 que amarra ferramenta, alvos (ids), ator, tenant e expiração; a etapa de execução só aceita o token e
 * executa exatamente os alvos assinados. Segue a orientação da spec MCP 2026-07-28 (estado entre chamadas como
 * handle mintado pelo servidor, passado como argumento comum), sem depender de sessão nem de elicitation.
 *
 * <p>Em cluster, todos os nós precisam do mesmo segredo ({@code kikwiflow.mcp.confirmation.secret}).
 */
public class ConfirmationTokenService {

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public ConfirmationTokenService(byte[] secret, Duration ttl, Clock clock) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("O segredo do token de confirmação precisa ter pelo menos 32 bytes.");
        }
        this.secret = secret.clone();
        this.ttl = Objects.requireNonNull(ttl);
        this.clock = Objects.requireNonNull(clock);
    }

    public Issued issue(String tool, List<String> targetIds, IdentityContext identity) {
        for (String id : targetIds) {
            if (id.indexOf(',') >= 0 || id.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Id inválido para token de confirmação: " + id);
            }
        }
        Instant expiresAt = clock.instant().plus(ttl);
        String payload = String.join("\n", VERSION, tool, nullToEmpty(identity.actorId()), nullToEmpty(identity.tenantId()),
                Long.toString(expiresAt.getEpochSecond()), String.join(",", targetIds));
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return new Issued(encodedPayload + "." + sign(encodedPayload), expiresAt);
    }

    /** @return os ids assinados, na ordem do preview. */
    public List<String> verify(String token, String tool, IdentityContext identity) {
        if (token == null || token.isBlank()) {
            throw invalid("Token de confirmação ausente.");
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw invalid("Token de confirmação malformado.");
        }
        String encodedPayload = token.substring(0, dot);
        byte[] expected = sign(encodedPayload).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, given)) {
            throw invalid("Token de confirmação inválido (assinatura não confere). Gere um novo preview.");
        }

        String[] parts;
        try {
            parts = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8).split("\n", -1);
        } catch (IllegalArgumentException e) {
            throw invalid("Token de confirmação malformado.");
        }
        if (parts.length != 6 || !VERSION.equals(parts[0])) {
            throw invalid("Token de confirmação de versão desconhecida.");
        }
        if (!tool.equals(parts[1])) {
            throw invalid("Este token foi emitido para outra ferramenta (" + parts[1] + ").");
        }
        if (!parts[2].equals(nullToEmpty(identity.actorId())) || !parts[3].equals(nullToEmpty(identity.tenantId()))) {
            throw invalid("Este token foi emitido para outro usuário/tenant. Gere um novo preview com a sua identidade.");
        }
        if (clock.instant().isAfter(Instant.ofEpochSecond(Long.parseLong(parts[4])))) {
            throw invalid("Token de confirmação expirado. Gere um novo preview.");
        }
        return parts[5].isEmpty() ? List.of() : Arrays.asList(parts[5].split(","));
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível na JVM", e);
        }
    }

    private static ToolException invalid(String message) {
        return new ToolException(ToolException.INVALID_CONFIRMATION, message);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Issued(String token, Instant expiresAt) {
    }
}
