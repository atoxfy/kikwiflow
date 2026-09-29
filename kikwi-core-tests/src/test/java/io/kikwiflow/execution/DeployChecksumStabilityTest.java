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
package io.kikwiflow.execution;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.model.definition.process.ProcessDefinitionDeployRequest;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.parser.jackson.JacksonProcessDefinitionParser;
import io.kikwiflow.parser.jackson.KikwiflowJacksonModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O checksum do deploy serializa o {@link ProcessDefinitionDeployRequest} com Jackson. Mapas copiados com
 * {@code Map.copyOf} iteram numa ordem sorteada a cada JVM, então o mesmo arquivo gerava um checksum diferente
 * a cada restart e o auto-deploy criava uma versão nova do processo sem nada ter mudado. Os mapas do request
 * precisam manter a ordem do JSON.
 */
@DisplayName("Dado um processo com extensionProperties de várias chaves")
class DeployChecksumStabilityTest {

    private static final List<String> KEYS = List.of("k8", "k3", "k6", "k1", "k7", "k2", "k5", "k4");

    private static final String PROCESS = """
            {
              "key": "checksum-estavel",
              "name": "Checksum estável",
              "variableDeclarations": [
                { "key": "cpf", "label": "CPF", "format": "SHORT_TEXT", "extensionProperties": %1$s }
              ],
              "flowNodes": {
                "START": {
                  "id": "START", "type": "DEFAULT_START_EVENT",
                  "outgoing": [ { "id": "f1", "targetNodeId": "END", "extensionProperties": %1$s } ]
                },
                "END": { "id": "END", "type": "DEFAULT_END_EVENT", "outgoing": [] }
              },
              "defaultStartPoint": "START"
            }
            """.formatted(jsonObject());

    private final JacksonProcessDefinitionParser parser = new JacksonProcessDefinitionParser(new ObjectMapper()
            .registerModule(new KikwiflowJacksonModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

    private static String jsonObject() {
        StringBuilder json = new StringBuilder("{");
        for (String key : KEYS) {
            json.append(json.length() > 1 ? ", " : "").append('"').append(key).append("\": \"v\"");
        }
        return json.append('}').toString();
    }

    private ProcessDefinitionDeployRequest parse() throws Exception {
        return parser.parse(PROCESS.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Quando o arquivo é lido")
    class QuandoLido {

        @Test
        @DisplayName("Então o catálogo mantém a ordem das extensionProperties do JSON")
        void catalogoMantemOrdem() throws Exception {
            assertEquals(KEYS, List.copyOf(parse().variableDeclarations().get(0).extensionProperties().keySet()));
        }

        @Test
        @DisplayName("Então as setas mantêm a ordem das extensionProperties do JSON")
        void setasMantemOrdem() throws Exception {
            SequenceFlowDefinition flow = parse().flowNodes().get("START").outgoing().get(0);
            assertEquals(KEYS, List.copyOf(flow.extensionProperties().keySet()));
        }

        @Test
        @DisplayName("Então duas leituras do mesmo arquivo dão o mesmo checksum")
        void checksumDeterministico() throws Exception {
            assertEquals(parser.calculateChecksum(parse()), parser.calculateChecksum(parse()));
        }
    }
}
