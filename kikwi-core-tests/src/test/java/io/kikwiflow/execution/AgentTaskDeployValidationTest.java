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

package io.kikwiflow.execution;

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KIKWI-067..071 (docs/engine/31): cada caso parte da fixture válida {@code agent-task.json} e quebra uma coisa só.
 */
@DisplayName("Dado um processo com AGENT_TASK sendo implantado")
class AgentTaskDeployValidationTest {

    private static final String OUTPUTS = """
            "outputs": [
                      { "variable": "resultado", "required": true },
                      { "variable": "fotosVeiculo", "required": false }
                    ]""";

    private TestEngine testEngine;
    private String validJson;

    @BeforeEach
    void setUp() throws Exception {
        testEngine = SingletonsFactory.engine().build();
        try (InputStream is = getClass().getResourceAsStream("/processes/agent-task.json")) {
            validJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ProcessDefinition deploy(String json) throws Exception {
        return testEngine.processDefinitionService().deploy(json.getBytes(StandardCharsets.UTF_8), IdentityContext.system());
    }

    private void assertRejected(String target, String replacement, String expectedMessagePart) {
        assertTrue(validJson.contains(target), "A fixture mudou: o trecho a substituir não existe mais: " + target);
        InvalidProcessDefinitionException ex = assertThrows(InvalidProcessDefinitionException.class,
                () -> deploy(validJson.replace(target, replacement)));
        assertTrue(ex.getMessage().contains(expectedMessagePart),
                "Mensagem deveria citar '" + expectedMessagePart + "', mas foi: " + ex.getMessage());
    }

    @Test
    @DisplayName("Quando a fixture é válida, então o deploy passa e o nó volta com agente, ferramentas e limites")
    void acceptsAValidAgentTask() throws Exception {
        ProcessDefinition definition = deploy(validJson);

        AgentTaskDefinition node = assertInstanceOf(AgentTaskDefinition.class, definition.flowNodes().get("QUALIFICAR"));
        assertEquals("kikwi.agents", node.topic());
        assertEquals(List.of("solicitar_midia", "agendar_visita"), node.tools());
        assertEquals("P7D", node.limits().ttl());
    }

    @Test
    @DisplayName("KIKWI-067: sem topic ou sem agentRef.key, então o deploy é rejeitado")
    void requiresTopicAndAgent() {
        assertRejected("\"topic\": \"kikwi.agents\",", "\"topic\": \" \",", "'topic' is empty");
        assertRejected("\"agentRef\": { \"key\": \"pre-vendas\", \"version\": null },", "", "'agentRef.key' is empty");
    }

    @Test
    @DisplayName("KIKWI-068: sem outputs, então o deploy é rejeitado")
    void requiresAnOutputContract() {
        assertRejected(OUTPUTS, "\"outputs\": []", "'variableBindings.outputs'");
    }

    @Test
    @DisplayName("KIKWI-069: um boundary fora da lista (ex.: timer não suportado por tipo) é rejeitado; error handler é aceito")
    void validatesBoundaries() {
        // O error handler OPT_OUT já está na fixture válida (aceito). Um nó que não é boundary na lista é rejeitado.
        assertRejected("\"boundaryEventIds\": [\"OPT_OUT\", \"HUMANO_ASSUMIU\"]",
                "\"boundaryEventIds\": [\"OPT_OUT\", \"HUMANO_ASSUMIU\", \"FIM_AGENDADO\"]", "AGENT_TASK");
    }

    @Test
    @DisplayName("KIKWI-070: ferramenta repetida ou ttl que não é duração ISO-8601, então o deploy é rejeitado")
    void validatesToolsAndLimits() {
        assertRejected("[\"solicitar_midia\", \"agendar_visita\"]", "[\"agendar_visita\", \"agendar_visita\"]",
                "listed more than once");
        assertRejected("\"ttl\": \"P7D\"", "\"ttl\": \"7 dias\"", "'limits.ttl'");
        assertRejected("\"maxTurns\": 80", "\"maxTurns\": 0", "'limits.maxTurns'");
    }

    @Test
    @DisplayName("KIKWI-071: kikwi:file.* fora de FILE, ou maxCount sem multiple, então o deploy é rejeitado")
    void validatesFileProperties() {
        assertRejected("{ \"key\": \"leadNome\", \"label\": \"Nome do lead\", \"format\": \"SHORT_TEXT\" }",
                "{ \"key\": \"leadNome\", \"label\": \"Nome do lead\", \"format\": \"SHORT_TEXT\", \"extensionProperties\": { \"kikwi:file.multiple\": \"true\" } }",
                "only allowed for FILE");
        assertRejected("\"kikwi:file.multiple\": \"true\", ", "", "requires 'kikwi:file.multiple' = true");
        assertRejected("\"kikwi:file.mimeTypes\": \"image/*\"", "\"kikwi:file.mimeTypes\": \"imagens\"", "invalid entry 'imagens'");
    }
}
