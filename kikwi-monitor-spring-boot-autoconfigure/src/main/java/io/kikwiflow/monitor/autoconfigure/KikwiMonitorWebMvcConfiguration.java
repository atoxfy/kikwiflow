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

import io.kikwiflow.monitor.autoconfigure.web.SpaFallbackResourceResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the Kikwiflow Monitor static SPA (bundled under {@code classpath:/static/} by the frontend's manual
 * {@code next build} + copy step) under {@link #MONITOR_UI_PATH}, with SPA client-side routing fallback.
 *
 * <p>{@code /monitor-ui} is intentionally a hardcoded constant, not a configuration property: the Next.js
 * {@code basePath} baked into the static bundle at build time must match it exactly, so a Spring-side property
 * would create a false sense of flexibility that silently breaks the embedded {@code /monitor-ui/_next/**}
 * assets if only one side is changed.
 */
@Configuration(proxyBeanMethods = false)
public class KikwiMonitorWebMvcConfiguration implements WebMvcConfigurer {

    public static final String MONITOR_UI_PATH = "/monitor-ui";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(MONITOR_UI_PATH + "/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }
}
