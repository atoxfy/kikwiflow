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

import io.kikwiflow.config.KikwiflowConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KikwiflowAutoConfiguration — kikwiflow.execution.task-acquisition-enabled")
class KikwiflowConfigMappingTest {

    private static KikwiflowConfig configFrom(Map<String, String> properties) {
        KikwiflowProperties bound = new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("kikwiflow", KikwiflowProperties.class);
        return new KikwiflowAutoConfiguration().kikwiflowConfig(bound);
    }

    @Test
    @DisplayName("Sem a propriedade, o nó adquire tarefas (comportamento de sempre)")
    void enabledByDefault() {
        assertTrue(configFrom(Map.of()).isTaskAcquisitionEnabled());
    }

    @Test
    @DisplayName("Com false, o nó fica passivo: só observa o banco, não executa tarefas")
    void passiveNode() {
        assertFalse(configFrom(Map.of("kikwiflow.execution.task-acquisition-enabled", "false"))
                .isTaskAcquisitionEnabled());
    }
}
