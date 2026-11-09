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

package io.kikwiflow.mcp.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Base dos links do Monitor nas respostas MCP")
class KikwiMcpBaseUrlTest {

    private final KikwiMcpProperties properties = new KikwiMcpProperties();
    private final MockEnvironment environment = new MockEnvironment();

    @Test
    @DisplayName("public-base-url vence a api-url do Monitor")
    void publicBaseUrlWins() {
        properties.setPublicBaseUrl("https://ops.empresa.com/");
        environment.setProperty("kikwiflow.monitor-ui.api-url", "https://api.empresa.com");

        assertEquals("https://ops.empresa.com", KikwiMcpAutoConfiguration.configuredBaseUrl(properties, environment));
    }

    @Test
    @DisplayName("Sem public-base-url, usa a api-url absoluta do Monitor (já é o endereço público)")
    void fallsBackToMonitorApiUrl() {
        environment.setProperty("kikwiflow.monitor-ui.api-url", "https://app.empresa.com/");

        String configured = KikwiMcpAutoConfiguration.configuredBaseUrl(properties, environment);

        assertEquals("https://app.empresa.com", configured);
        assertEquals("https://app.empresa.com", KikwiMcpAutoConfiguration.baseUrl(internalRequest(), configured));
    }

    @Test
    @DisplayName("api-url relativa ou ausente: deriva da requisição")
    void derivesFromRequestOtherwise() {
        environment.setProperty("kikwiflow.monitor-ui.api-url", "/");

        String configured = KikwiMcpAutoConfiguration.configuredBaseUrl(properties, environment);

        assertNull(configured);
        assertEquals("http://10.0.3.7:8080", KikwiMcpAutoConfiguration.baseUrl(internalRequest(), configured));
    }

    private static MockHttpServletRequest internalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/kikwiflow/api/v1/mcp");
        request.setScheme("http");
        request.setServerName("10.0.3.7");
        request.setServerPort(8080);
        return request;
    }
}
