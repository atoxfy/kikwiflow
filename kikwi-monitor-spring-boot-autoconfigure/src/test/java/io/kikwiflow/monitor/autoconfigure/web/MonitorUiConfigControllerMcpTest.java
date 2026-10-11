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

package io.kikwiflow.monitor.autoconfigure.web;

import io.kikwiflow.monitor.autoconfigure.properties.MonitorUiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("config.json — bloco mcp do diálogo \"Conectar agente\"")
class MonitorUiConfigControllerMcpTest {

    private final MockEnvironment environment = new MockEnvironment();

    private MonitorUiConfigResponse.Mcp mcp(String apiUrl) {
        MonitorUiProperties properties = new MonitorUiProperties(true, apiUrl, "", "", "", true, false,
                new MonitorUiProperties.ActingAs(List.of(), "X-User-Id"));
        return new MonitorUiConfigController(properties, environment).config().mcp();
    }

    @Test
    @DisplayName("MCP desligado: sem URL, e o Monitor esconde o botão")
    void disabled() {
        assertThat(mcp("https://app.empresa.com")).isEqualTo(new MonitorUiConfigResponse.Mcp(false, null, false));
    }

    @Test
    @DisplayName("URL = api-url pública + base path padrão do REST + /mcp")
    void defaultPath() {
        environment.setProperty("kikwiflow.mcp.enabled", "true");

        assertThat(mcp("https://app.empresa.com/").url()).isEqualTo("https://app.empresa.com/kikwiflow/api/v1/mcp");
    }

    @Test
    @DisplayName("Segue um kikwiflow.rest.base-path customizado (com ou sem barra final)")
    void customRestBasePath() {
        environment.setProperty("kikwiflow.mcp.enabled", "true");
        environment.setProperty("kikwiflow.rest.base-path", "/ops/api/");

        assertThat(mcp("https://app.empresa.com").url()).isEqualTo("https://app.empresa.com/ops/api/mcp");
    }

    @Test
    @DisplayName("kikwiflow.mcp.path explícito vence o base path")
    void explicitMcpPath() {
        environment.setProperty("kikwiflow.mcp.enabled", "true");
        environment.setProperty("kikwiflow.rest.base-path", "/ops/api");
        environment.setProperty("kikwiflow.mcp.path", "/agentes/mcp");

        assertThat(mcp("https://app.empresa.com").url()).isEqualTo("https://app.empresa.com/agentes/mcp");
    }

    @Test
    @DisplayName("api-url vazia: caminho relativo, que o navegador completa com a origem do Monitor")
    void relativeWhenApiUrlEmpty() {
        environment.setProperty("kikwiflow.mcp.enabled", "true");

        assertThat(mcp("").url()).isEqualTo("/kikwiflow/api/v1/mcp");
        assertThat(mcp(null).url()).isEqualTo("/kikwiflow/api/v1/mcp");
    }

    @Test
    @DisplayName("writeTools reflete kikwiflow.mcp.write-tools.enabled")
    void writeTools() {
        environment.setProperty("kikwiflow.mcp.enabled", "true");
        environment.setProperty("kikwiflow.mcp.write-tools.enabled", "true");

        assertThat(mcp("https://app.empresa.com").writeTools()).isTrue();
    }
}
