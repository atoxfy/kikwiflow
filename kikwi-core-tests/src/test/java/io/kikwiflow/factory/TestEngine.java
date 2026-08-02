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

package io.kikwiflow.factory;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.assertion.AssertableKikwiEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.navigation.ProcessDefinitionService;

import java.io.InputStream;

/**
 * Agrupa as três peças que um teste de motor precisa: o {@link KikwiflowEngine} para iniciar/completar
 * processos, o {@link AssertableKikwiEngine} (repositório + asserções) para inspecionar estado, e o
 * {@link ProcessDefinitionService} para implantar fixtures de processo.
 */
public record TestEngine(KikwiflowEngine engine, AssertableKikwiEngine repository,
                          ProcessDefinitionService processDefinitionService) {

    /**
     * Implanta uma definição de processo a partir de um arquivo `.json` no classpath de teste
     * (ex.: {@code "/processes/executable-task-flow.json"}).
     */
    public ProcessDefinition deploy(String classpathResource) {
        try (InputStream is = getClass().getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture de processo não encontrada no classpath: " + classpathResource);
            }
            byte[] content = is.readAllBytes();
            return processDefinitionService.deploy(content, IdentityContext.system());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao implantar fixture de teste: " + classpathResource, e);
        }
    }
}
