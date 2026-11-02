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
package io.kikwiflow.connector.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Registra o {@link HttpTaskHandler} como o bean {@code kikwiHttp} — o nome que o {@code executor} do nó referencia.
 * Ligado por padrão quando o módulo está no classpath; {@code kikwiflow.connectors.http.enabled=false} desliga.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kikwiflow.connectors.http", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HttpConnectorAutoConfiguration {

    @Bean(HttpTaskHandler.BEAN_NAME)
    @ConditionalOnMissingBean(name = HttpTaskHandler.BEAN_NAME)
    HttpTaskHandler kikwiHttp(ObjectProvider<ObjectMapper> mapper, Environment environment) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new HttpTaskHandler(client, mapper.getIfAvailable(ObjectMapper::new), environment::resolveRequiredPlaceholders);
    }
}
