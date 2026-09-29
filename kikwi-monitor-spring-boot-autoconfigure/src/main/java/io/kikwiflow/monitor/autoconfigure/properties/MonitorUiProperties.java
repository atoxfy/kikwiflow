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

package io.kikwiflow.monitor.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configures the Kikwiflow Monitor (Pulse) UI embedded by {@code kikwi-monitor-spring-boot-autoconfigure}.
 *
 * <p>This lib only serves the static SPA and this configuration as JSON at {@code /monitor-ui/config.json} —
 * it never talks to the engine itself. The browser reads {@code apiUrl} (and the OIDC/auth fields) from that
 * endpoint and then calls {@code kikwi-management-rest} directly, wherever it is actually hosted.
 */
@ConfigurationProperties(prefix = "kikwiflow.monitor-ui")
public record MonitorUiProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:8081") String apiUrl,
        @DefaultValue("") String oidcAuthority,
        @DefaultValue("") String oidcClientId,
        @DefaultValue("") String oidcRedirectUri,
        @DefaultValue("true") boolean requireAuth,
        @DefaultValue("false") boolean readOnly,
        @DefaultValue ActingAs actingAs
) {

    /**
     * {@code kikwiflow.monitor-ui.acting-as.*} — seletor "Atuando como" do Monitor, para ambientes sem login
     * (demo, homologação): o usuário escolhe um dos {@code actors} e o Monitor manda o nome no cabeçalho
     * {@code header} em cada chamada à API de gestão. Sem {@code actors}, o seletor não aparece; com
     * {@code require-auth=true}, também não (o usuário vem do token).
     * <p>
     * O Monitor só <b>envia</b> o cabeçalho. Quem decide confiar nele é o {@code HttpIdentityResolver} do host —
     * o padrão do Kikwiflow não lê esse cabeçalho, e em produção o usuário deve vir do token autenticado.
     */
    public record ActingAs(
            @DefaultValue List<String> actors,
            @DefaultValue("X-User-Id") String header
    ) {
    }
}
