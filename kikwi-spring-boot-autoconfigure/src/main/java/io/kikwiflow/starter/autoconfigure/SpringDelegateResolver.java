/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.starter.autoconfigure;

import io.kikwiflow.execution.DelegateResolver;
import io.kikwiflow.model.execution.api.JavaDelegate;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

public class SpringDelegateResolver implements DelegateResolver {

    private final ApplicationContext applicationContext;

    public SpringDelegateResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Optional<JavaDelegate> resolve(String beanName) {
        try {
            return Optional.of(applicationContext.getBean(beanName, JavaDelegate.class));
        } catch (NoSuchBeanDefinitionException e) {
            return Optional.empty();
        }
    }
}