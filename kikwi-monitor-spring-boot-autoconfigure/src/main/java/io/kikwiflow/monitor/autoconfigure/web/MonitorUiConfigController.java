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

import io.kikwiflow.monitor.autoconfigure.KikwiMonitorWebMvcConfiguration;
import io.kikwiflow.monitor.autoconfigure.properties.MonitorUiProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@code GET /monitor-ui/config.json} — the only thing the embedded Monitor SPA needs at boot to know
 * where the real engine API lives and how to authenticate against it. This lib never talks to the engine
 * itself; it only reflects {@link MonitorUiProperties}, which the host application configures.
 */
@RestController
public class MonitorUiConfigController {

    private final MonitorUiProperties properties;

    public MonitorUiConfigController(MonitorUiProperties properties) {
        this.properties = properties;
    }

    @GetMapping(path = KikwiMonitorWebMvcConfiguration.MONITOR_UI_PATH + "/config.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public MonitorUiConfigResponse config() {
        return new MonitorUiConfigResponse(
                properties.apiUrl(),
                properties.oidcAuthority(),
                properties.oidcClientId(),
                properties.oidcRedirectUri(),
                properties.requireAuth(),
                properties.readOnly()
        );
    }
}
