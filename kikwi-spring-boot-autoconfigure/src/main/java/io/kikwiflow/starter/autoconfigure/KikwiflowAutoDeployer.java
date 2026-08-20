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
package io.kikwiflow.starter.autoconfigure;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.model.security.IdentityContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.InputStream;

public class KikwiflowAutoDeployer implements ApplicationRunner {

    private final ProcessDefinitionService processDefinitionService;
    private final ResourcePatternResolver resourcePatternResolver;
    private final String locationPattern;

    public KikwiflowAutoDeployer(ProcessDefinitionService processDefinitionService,
                                 ResourcePatternResolver resourcePatternResolver,
                                 String locationPattern) {

        this.processDefinitionService = processDefinitionService;
        this.resourcePatternResolver = resourcePatternResolver;
        this.locationPattern = locationPattern;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {

            Resource[] resources = resourcePatternResolver.getResources(locationPattern);
            if (resources.length == 0) {
                System.out.println("Kikwiflow AutoDeploy: Nenhum processo encontrado em " + locationPattern);
                return;
            }

            int count = 0;
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {

                    byte[] content = is.readAllBytes();

                    ProcessDefinition processDef = processDefinitionService.deploy(content, IdentityContext.system());
                    count++;
                    System.out.println(" ✓ Processo deployado: " + processDef.key() + " (v" + processDef.version() + ")");
                } catch (Exception e) {
                    System.err.println( "Kikwiflow AutoDeploy: Falha ao fazer parse/deploy do arquivo " + resource.getFilename() + " - " + e.getMessage());
                }
            }

            System.out.println(" ✓ AutoDeploy concluído:  (" + count + ") processos");
        } catch (Exception e) {
            System.err.println("Kikwiflow AutoDeploy: Erro crítico ao escanear diretório: " + e.getMessage());
        }
    }
}