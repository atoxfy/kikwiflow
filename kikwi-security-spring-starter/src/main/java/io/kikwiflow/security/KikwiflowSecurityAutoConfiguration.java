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
package io.kikwiflow.security;

import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.security.HttpIdentityResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class KikwiflowSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpIdentityResolver.class)
    public HttpIdentityResolver anonymousIdentityResolver() {
        return request -> {
            String tenantId = request.getHeader("X-Tenant-Id");
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = "DEFAULT";
            }

            return new IdentityContext(
                    "anonymous",
                    tenantId,
                    Collections.emptySet()
            );
        };
    }
}