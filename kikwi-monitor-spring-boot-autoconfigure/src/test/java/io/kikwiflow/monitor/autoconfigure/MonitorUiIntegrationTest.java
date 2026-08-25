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

import io.kikwiflow.monitor.autoconfigure.support.TestMonitorApplication;
import io.kikwiflow.monitor.autoconfigure.web.MonitorUiConfigResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration coverage over a real embedded servlet container ({@link TestRestTemplate}), exercising the
 * actual HTTP behavior of the resource handler, the SPA fallback, and the config endpoint.
 *
 * <p>Static fixtures under {@code src/test/resources/static/} (dummy {@code index.html}, {@code monitor.html},
 * {@code _not-found.html}, {@code assets/app.css} — mirroring the shape of a real Next.js
 * {@code output: 'export'} bundle, one pre-rendered HTML per route) are used instead of the real Next.js
 * bundle on purpose: Maven puts {@code target/test-classes} ahead of
 * {@code target/classes} on the test classpath, so these tests exercise the same mechanism regardless of
 * whether the real Monitor bundle has already been copied into {@code src/main/resources/static} — they
 * validate behavior, not bundle content, and always run without depending on the frontend build.
 */
@SpringBootTest(
        classes = TestMonitorApplication.class,
        webEnvironment = RANDOM_PORT,
        properties = {
                "kikwiflow.monitor-ui.api-url=http://test-engine:9999",
                "kikwiflow.monitor-ui.oidc-authority=https://auth.example.com",
                "kikwiflow.monitor-ui.oidc-client-id=kikwiflow-test",
                "kikwiflow.monitor-ui.oidc-redirect-uri=http://localhost:3000/",
                "kikwiflow.monitor-ui.require-auth=false",
                "kikwiflow.monitor-ui.read-only=true"
        }
)
@DisplayName("Monitor UI - integração HTTP real")
class MonitorUiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Nested
    @DisplayName("GET /monitor-ui/config.json")
    class ConfigJson {

        @Test
        @DisplayName("Então reflete os valores configurados em MonitorUiProperties")
        void entaoRefleteValoresConfigurados() {
            ResponseEntity<MonitorUiConfigResponse> response =
                    restTemplate.getForEntity("/monitor-ui/config.json", MonitorUiConfigResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            MonitorUiConfigResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.apiUrl()).isEqualTo("http://test-engine:9999");
            assertThat(body.oidcAuthority()).isEqualTo("https://auth.example.com");
            assertThat(body.oidcClientId()).isEqualTo("kikwiflow-test");
            assertThat(body.oidcRedirectUri()).isEqualTo("http://localhost:3000/");
            assertThat(body.requireAuth()).isFalse();
            assertThat(body.readOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET da raiz /monitor-ui (com e sem barra final)")
    class RaizDoMonitorUi {

        @Test
        @DisplayName("Então serve o index.html em vez de 404 — resourcePath vazio não passa pelo resource handler")
        void entaoServeIndexHtmlSemBarraFinal() {
            ResponseEntity<String> response = restTemplate.getForEntity("/monitor-ui", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("KIKWI-MONITOR-TEST-FIXTURE");
        }

        @Test
        @DisplayName("Então serve o index.html em vez de 404 — com barra final")
        void entaoServeIndexHtmlComBarraFinal() {
            ResponseEntity<String> response = restTemplate.getForEntity("/monitor-ui/", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("KIKWI-MONITOR-TEST-FIXTURE");
        }
    }

    @Nested
    @DisplayName("GET de um asset estático real")
    class AssetEstatico {

        @Test
        @DisplayName("Então retorna 200 com o content-type correto e o conteúdo do fixture")
        void entaoRetorna200ComContentTypeCorreto() {
            ResponseEntity<String> response = restTemplate.getForEntity("/monitor-ui/assets/app.css", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.valueOf("text/css"))).isTrue();
            assertThat(response.getBody()).contains("color: red");
        }
    }

    @Nested
    @DisplayName("GET de uma rota conhecida (com query string) sem asset exato correspondente")
    class FallbackParaPaginaConhecida {

        // Regressão: servir sempre index.html aqui renderizaria a tela de overview em vez da tela de
        // detalhe do processo em qualquer F5/deep-link em /monitor?processId=... — bug real pego só na
        // verificação manual no browser, não no smoke-check via curl. O Next.js (output: 'export') gera
        // um HTML pré-renderizado por rota (monitor.html), não um único shell genérico como um SPA
        // clássico — por isso o fallback precisa tentar "<path>.html" antes de recorrer a index.html.
        @Test
        @DisplayName("Então serve o HTML pré-renderizado daquela rota (ex.: monitor.html), não o index.html")
        void entaoServePaginaPreRenderizadaDaRota() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity("/monitor-ui/monitor?processId=algum-id", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
            assertThat(response.getBody()).contains("KIKWI-MONITOR-TEST-FIXTURE-MONITOR-PAGE");
        }
    }

    @Nested
    @DisplayName("GET de uma rota totalmente desconhecida, sob /monitor-ui/**")
    class FallbackParaRotaDesconhecida {

        @Test
        @DisplayName("Então serve o _not-found.html do Next em vez de 404 (nenhuma página pré-renderizada bate com o path)")
        void entaoServeNotFoundHtml() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity("/monitor-ui/rota/totalmente/desconhecida", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
            assertThat(response.getBody()).contains("KIKWI-MONITOR-TEST-FIXTURE-NOT-FOUND");
        }
    }

    @Nested
    @DisplayName("GET de um path fora de /monitor-ui/**")
    class ForaDoPrefixo {

        @Test
        @DisplayName("Então não é afetado pelo fallback SPA e retorna 404 puro")
        void entaoRetorna404Puro() {
            ResponseEntity<String> response =
                    restTemplate.getForEntity("/outro-caminho-qualquer", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
