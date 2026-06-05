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

package io.kikwiflow.rest.autoconfigure;

import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.rest.autoconfigure.properties.KikwiflowRestProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class KikwiflowWebMvcAutoConfiguration implements WebMvcConfigurer {

    private final KikwiflowRestProperties properties;

    public KikwiflowWebMvcAutoConfiguration(KikwiflowRestProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                properties.getBasePath(),
                HandlerTypePredicate.forAnnotation(KikwiRestController.class)
        );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.getCors().getAllowedOrigins();
        if (origins != null && origins.length > 0) {
            registry.addMapping(properties.getBasePath() + "/**")
                    .allowedOrigins(origins)
                    .allowedMethods("*")
                    .allowCredentials(true);
        }
    }
}