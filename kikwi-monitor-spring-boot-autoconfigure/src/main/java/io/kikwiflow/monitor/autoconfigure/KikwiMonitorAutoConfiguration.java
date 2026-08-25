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

package io.kikwiflow.monitor.autoconfigure;

import io.kikwiflow.monitor.autoconfigure.properties.MonitorUiProperties;
import io.kikwiflow.monitor.autoconfigure.web.MonitorUiConfigController;
import io.kikwiflow.monitor.autoconfigure.web.MonitorUiRootController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Serves the Kikwiflow Monitor (Pulse) static SPA and its runtime config endpoint under {@code /monitor-ui}.
 *
 * <p>This module is deliberately standalone: it has no dependency on {@code kikwi-core},
 * {@code kikwi-management-rest}, or {@code kikwi-security-*}. It never talks to the engine — the browser
 * calls {@code kikwi-management-rest} directly, using the {@code apiUrl} this module hands it via
 * {@code GET /monitor-ui/config.json}. CORS and identity/auth stay entirely the responsibility of whoever
 * hosts the engine (see {@code kikwiflow.rest.cors.allowed-origins} and
 * {@code io.kikwiflow.security.HttpIdentityResolver} in {@code kikwi-security-spring-starter}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "kikwiflow.monitor-ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MonitorUiProperties.class)
@Import({KikwiMonitorWebMvcConfiguration.class, MonitorUiConfigController.class, MonitorUiRootController.class})
public class KikwiMonitorAutoConfiguration {
}
