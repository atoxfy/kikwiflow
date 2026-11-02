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

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessContract;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrato de retorno do processo (docs/engine/26): {@code outputs} nos fins, {@link ProcessContract} visto de fora
 * e o logo {@code kikwi:icon} (KIKWI-072). A fixture {@code process-tool.json} é um processo pensado para ser
 * chamado como ferramenta de agente.
 */
@DisplayName("Dado um processo que declara o que retorna em cada fim")
class ProcessContractTest {

    private static final String RECUSADO_OUTPUTS = "\"variableBindings\": { \"outputs\": [\n"
            + "        { \"variable\": \"protocolo\", \"required\": true },\n"
            + "        { \"variable\": \"motivo\"";

    private TestEngine testEngine;
    private String validJson;

    @BeforeEach
    void setUp() throws Exception {
        testEngine = SingletonsFactory.engine().build();
        try (InputStream is = getClass().getResourceAsStream("/processes/process-tool.json")) {
            validJson = new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
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

    private static Map<String, ProcessVariable> vars(Object... keyValues) {
        Map<String, ProcessVariable> variables = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            variables.put(key, new ProcessVariable(key, keyValues[i + 1]));
        }
        return variables;
    }

    @Nested
    @DisplayName("Quando é implantado")
    class Deploy {

        @Test
        @DisplayName("Então os outputs dos fins e o logo voltam na definição")
        void keepsEndOutputsAndIcon() throws Exception {
            ProcessDefinition definition = deploy(validJson);

            EndEventDefinition agendado = assertInstanceOf(EndEventDefinition.class, definition.flowNodes().get("AGENDADO"));
            assertEquals(List.of("protocolo", "agendamentoId", "dataHora"),
                    agendado.variableBindingsOrEmpty().outputs().stream().map(b -> b.variable()).toList());
            assertTrue(definition.icon().orElseThrow().startsWith("data:image/svg+xml"));
        }

        @Test
        @DisplayName("KIKWI-064: um output de fim fora do catálogo é rejeitado")
        void rejectsUnknownOutput() {
            assertRejected("{ \"variable\": \"motivo\", \"required\": true }",
                    "{ \"variable\": \"naoDeclarada\", \"required\": true }", "naoDeclarada");
        }

        @Test
        @DisplayName("KIKWI-065: inputs num fim são rejeitados — o fim só declara o que retorna")
        void rejectsInputsOnEnd() {
            assertRejected(RECUSADO_OUTPUTS,
                    RECUSADO_OUTPUTS.replace("{ \"outputs\"", "{ \"inputs\": [ { \"variable\": \"loja\" } ], \"outputs\""),
                    "'variableBindings.inputs' is not supported");
        }

        @Test
        @DisplayName("KIKWI-072: logo que não é SVG em data URI é rejeitado")
        void rejectsNonSvgIcon() {
            assertRejected("data:image/svg+xml;base64,", "data:image/png;base64,", "kikwi:icon");
        }

        @Test
        @DisplayName("KIKWI-072: logo grande demais é rejeitado")
        void rejectsHugeIcon() {
            assertRejected("PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=", "A".repeat(48_001), "exceeds");
        }
    }

    @Nested
    @DisplayName("Quando visto de fora como ferramenta")
    class Contract {

        @Test
        @DisplayName("Então inputs vêm do start e outputs são a união dos fins, obrigatórios só se todos os fins exigem")
        void derivesContract() throws Exception {
            ProcessContract contract = ProcessContract.of(deploy(validJson));

            assertEquals(List.of("dataHora", "loja"), contract.inputs().stream().map(ProcessContract.Field::variable).toList());
            assertTrue(contract.inputs().get(0).required());
            assertFalse(contract.inputs().get(1).required());

            Map<String, Boolean> outputs = new HashMap<>();
            contract.outputs().forEach(field -> outputs.put(field.variable(), field.required()));
            assertEquals(Map.of("protocolo", true, "agendamentoId", false, "dataHora", false, "motivo", false), outputs);
            assertEquals("Data e hora", contract.outputs().stream()
                    .filter(f -> f.variable().equals("dataHora")).findFirst().orElseThrow().declaration().label());
        }

        @Test
        @DisplayName("Então quem chama recebe só o que o processo retorna, nunca o estado interno")
        void projectsOnlyReturnedVariables() throws Exception {
            ProcessContract contract = ProcessContract.of(deploy(validJson));

            ProcessInstance finished = testEngine.engine().startProcess()
                    .byKey("agendar-avaliacao")
                    .withBusinessKey("lead-1")
                    .withVariables(vars("dataHora", "2026-11-03T10:00:00Z", "loja", "Centro", "decisao", "OK",
                            "protocolo", "P-1", "agendamentoId", "A-9", "rascunhoInterno", "não vaza"))
                    .execute();

            Map<String, ProcessVariable> returned = contract.project(finished.variables());
            assertEquals(List.of("protocolo", "agendamentoId", "dataHora"), List.copyOf(returned.keySet()));
            assertFalse(returned.containsKey("rascunhoInterno"));
            assertFalse(returned.containsKey("decisao"));
        }
    }
}
