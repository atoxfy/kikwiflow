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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level ({@link ApplicationContextRunner}, no real servlet container) coverage of
 * {@link KikwiMonitorAutoConfiguration}'s conditional bean registration. Complements
 * {@code MonitorUiIntegrationTest}, which exercises the actual HTTP behavior over a real embedded server.
 */
@DisplayName("KikwiMonitorAutoConfiguration")
class KikwiMonitorAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KikwiMonitorAutoConfiguration.class));

    @Nested
    @DisplayName("Quando a aplicação é web (servlet)")
    class QuandoAplicacaoEhWeb {

        @Test
        @DisplayName("Então registra as beans do Monitor UI por padrão")
        void entaoRegistraBeansPorPadrao() {
            webContextRunner.run(context -> {
                assertThat(context).hasSingleBean(MonitorUiProperties.class);
                assertThat(context).hasSingleBean(KikwiMonitorWebMvcConfiguration.class);
                assertThat(context).hasSingleBean(MonitorUiConfigController.class);
                assertThat(context).hasSingleBean(MonitorUiRootController.class);
            });
        }

        @Test
        @DisplayName("Quando kikwiflow.monitor-ui.enabled=false, então nenhuma bean é registrada")
        void quandoDesabilitadoEntaoNaoRegistraBeans() {
            webContextRunner
                    .withPropertyValues("kikwiflow.monitor-ui.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(MonitorUiProperties.class);
                        assertThat(context).doesNotHaveBean(KikwiMonitorWebMvcConfiguration.class);
                        assertThat(context).doesNotHaveBean(MonitorUiConfigController.class);
                        assertThat(context).doesNotHaveBean(MonitorUiRootController.class);
                    });
        }

        @Test
        @DisplayName("Quando properties customizadas são fornecidas, então MonitorUiProperties reflete os valores")
        void quandoPropertiesCustomizadasEntaoReflemNoBean() {
            webContextRunner
                    .withPropertyValues(
                            "kikwiflow.monitor-ui.api-url=https://engine.example.com",
                            "kikwiflow.monitor-ui.require-auth=false",
                            "kikwiflow.monitor-ui.read-only=true"
                    )
                    .run(context -> {
                        MonitorUiProperties properties = context.getBean(MonitorUiProperties.class);
                        assertThat(properties.apiUrl()).isEqualTo("https://engine.example.com");
                        assertThat(properties.requireAuth()).isFalse();
                        assertThat(properties.readOnly()).isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("Quando a aplicação não é web")
    class QuandoAplicacaoNaoEhWeb {

        private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KikwiMonitorAutoConfiguration.class));

        @Test
        @DisplayName("Então a auto-configuration recua e nenhuma bean é registrada")
        void entaoAutoConfigurationRecua() {
            nonWebContextRunner.run(context -> {
                assertThat(context).doesNotHaveBean(MonitorUiProperties.class);
                assertThat(context).doesNotHaveBean(KikwiMonitorWebMvcConfiguration.class);
                assertThat(context).doesNotHaveBean(MonitorUiConfigController.class);
                assertThat(context).doesNotHaveBean(MonitorUiRootController.class);
            });
        }
    }
}
