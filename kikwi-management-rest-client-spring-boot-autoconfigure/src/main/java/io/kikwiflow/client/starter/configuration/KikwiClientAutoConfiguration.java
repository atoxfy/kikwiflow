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
package io.kikwiflow.client.starter.configuration;

import io.kikwiflow.spring.rest.api.command.ExternalTaskOperationsRestApi;
import io.kikwiflow.spring.rest.api.command.ProcessDefinitionOperationsRestApi;
import io.kikwiflow.spring.rest.api.command.ProcessInstanceOperationsRestApi;
import io.kikwiflow.spring.rest.api.query.ExternalTaskQueryRestApi;
import io.kikwiflow.spring.rest.api.query.ProcessDefinitionQueryRestApi;
import io.kikwiflow.spring.rest.api.query.ProcessInstanceQueryRestApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@AutoConfiguration
public class KikwiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestClient kikwiRestClient(RestClient.Builder builder,
                                      @Value("${kikwiflow.client.url:http://localhost:8080}") String url) {
        return builder
                .baseUrl(url)
                .build();
    }

    @Bean
    public ProcessInstanceOperationsRestApi processInstanceOperationsClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ProcessInstanceOperationsRestApi.class);
    }

    @Bean
    public ProcessInstanceQueryRestApi processInstanceQueryClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ProcessInstanceQueryRestApi.class);
    }

    @Bean
    public ExternalTaskOperationsRestApi externalTaskOperationsClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ExternalTaskOperationsRestApi.class);
    }

    @Bean
    public ExternalTaskQueryRestApi externalTaskQueryClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ExternalTaskQueryRestApi.class);
    }

    @Bean
    public ProcessDefinitionOperationsRestApi processDefinitionOperationsClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ProcessDefinitionOperationsRestApi.class);
    }

    @Bean
    public ProcessDefinitionQueryRestApi processDefinitionQueryClient(RestClient kikwiRestClient) {
        return createClient(kikwiRestClient, ProcessDefinitionQueryRestApi.class);
    }


    private <T> T createClient(RestClient restClient, Class<T> interfaceClass) {
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(interfaceClass);
    }
}