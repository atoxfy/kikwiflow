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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Token de confirmação stateless das ferramentas de escrita")
class ConfirmationTokenServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final IdentityContext ANA = new IdentityContext("ana", "t1");

    private final ConfirmationTokenService service =
            new ConfirmationTokenService(SECRET, Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Dado um preview, Quando o mesmo usuário confirma, Então recebe exatamente os ids assinados")
    void roundTrip() {
        String token = service.issue("retry_incidents", List.of("i-1", "i-2"), ANA).token();

        assertEquals(List.of("i-1", "i-2"), service.verify(token, "retry_incidents", ANA));
    }

    @Test
    @DisplayName("Quando o token é adulterado, Então é recusado")
    void tampered() {
        String token = service.issue("retry_incidents", List.of("i-1"), ANA).token();
        String forged = new ConfirmationTokenService(SECRET, Duration.ofMinutes(5), Clock.fixed(NOW, ZoneOffset.UTC))
                .issue("retry_incidents", List.of("i-1", "i-999"), ANA).token();
        String mixed = forged.substring(0, forged.indexOf('.')) + token.substring(token.indexOf('.'));

        assertInvalid(() -> service.verify(mixed, "retry_incidents", ANA));
    }

    @Test
    @DisplayName("Quando outro usuário ou outro tenant tenta usar o token, Então é recusado")
    void otherIdentity() {
        String token = service.issue("retry_incidents", List.of("i-1"), ANA).token();

        assertInvalid(() -> service.verify(token, "retry_incidents", new IdentityContext("bruno", "t1")));
        assertInvalid(() -> service.verify(token, "retry_incidents", new IdentityContext("ana", "t2")));
    }

    @Test
    @DisplayName("Quando o token é de outra ferramenta, Então é recusado")
    void otherTool() {
        String token = service.issue("retry_incidents", List.of("i-1"), ANA).token();

        assertInvalid(() -> service.verify(token, "start_process_instance", ANA));
    }

    @Test
    @DisplayName("Quando o prazo expira, Então o token é recusado")
    void expired() {
        String token = service.issue("retry_incidents", List.of("i-1"), ANA).token();
        ConfirmationTokenService later = new ConfirmationTokenService(SECRET, Duration.ofMinutes(5),
                Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC));

        ToolException error = assertThrows(ToolException.class, () -> later.verify(token, "retry_incidents", ANA));
        assertTrue(error.getMessage().contains("expirado"));
    }

    @Test
    @DisplayName("Segredo curto demais é rejeitado na construção")
    void shortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfirmationTokenService("curto".getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5), Clock.systemUTC()));
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
        ToolException error = assertThrows(ToolException.class, executable);
        assertEquals(ToolException.INVALID_CONFIRMATION, error.getCode());
    }
}
