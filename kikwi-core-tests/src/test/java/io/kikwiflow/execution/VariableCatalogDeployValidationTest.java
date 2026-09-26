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
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;
import io.kikwiflow.validation.DeployValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * KIKWI-060..065 (docs/engine/14 e docs/engine/26): o {@link DeployValidator} rejeita no deploy catálogos de
 * variáveis malformados e vínculos órfãos, em vez de deixá-los estourar no primeiro start/complete.
 */
@DisplayName("Dado um processo com catálogo de variáveis sendo implantado")
class VariableCatalogDeployValidationTest {

    // Nenhum dos nós usados aqui exige resolver de bean — exceto o EXECUTABLE_TASK do KIKWI-065, que falha antes.
    private final DeployValidator deployValidator = new DeployValidator(name -> Optional.empty(), null, null);

    private static VariableDeclaration.Builder text(String key) {
        return VariableDeclaration.builder().key(key).label(key).format(VariableFormat.SHORT_TEXT);
    }

    private static ProcessDefinition process(List<VariableDeclaration> catalog, VariableBinding... startInputs) {
        StartEventDefinition start = StartEventDefinition.builder()
                .id("START").name("Start")
                .outgoing(List.of(new SequenceFlowDefinition("f1", null, null, null, "END", false, false, null, Map.of(), null, null)))
                .variableBindings(new VariableBindings(List.of(startInputs), null))
                .build();
        return process(catalog, start);
    }

    private static ProcessDefinition process(List<VariableDeclaration> catalog, FlowNodeDefinition start) {
        return ProcessDefinition.builder()
                .key("catalog-test")
                .defaultStartPoint("START")
                .flowNodes(Map.of("START", start, "END", EndEventDefinition.builder().id("END").name("End").build()))
                .variableDeclarations(catalog)
                .build();
    }

    private void assertRejected(ProcessDefinition definition) {
        assertThrows(InvalidProcessDefinitionException.class, () -> deployValidator.validate(definition));
    }

    @Test
    @DisplayName("Quando catálogo e vínculos são válidos, então o deploy passa")
    void acceptsValidCatalog() {
        assertDoesNotThrow(() -> deployValidator.validate(process(List.of(
                text("name").validationRegex("^[A-Z].*").build(),
                VariableDeclaration.builder().key("amount").label("Valor").format(VariableFormat.MONEY).currency("BRL").build(),
                VariableDeclaration.builder().key("seg").label("Seg").format(VariableFormat.SINGLE_SELECT)
                        .options(List.of(new VariableOption("PF", "PF"))).build()),
                new VariableBinding("name", true), new VariableBinding("amount", false), new VariableBinding("seg", null))));
    }

    @Test
    @DisplayName("KIKWI-060: key duplicada no catálogo é rejeitada")
    void rejectsDuplicateKey() {
        assertRejected(process(List.of(text("a").build(), text("a").build())));
    }

    @Test
    @DisplayName("KIKWI-060: declaração sem format é rejeitada")
    void rejectsMissingFormat() {
        assertRejected(process(List.of(VariableDeclaration.builder().key("a").label("A").build())));
    }

    @Test
    @DisplayName("KIKWI-061: SINGLE_SELECT sem options é rejeitado")
    void rejectsSelectWithoutOptions() {
        assertRejected(process(List.of(VariableDeclaration.builder().key("a").label("A").format(VariableFormat.SINGLE_SELECT).build())));
    }

    @Test
    @DisplayName("KIKWI-061: options em formato que não é select é rejeitado")
    void rejectsOptionsOnNonSelect() {
        assertRejected(process(List.of(text("a").options(List.of(new VariableOption("x", "x"))).build())));
    }

    @Test
    @DisplayName("KIKWI-062: MONEY sem currency válida é rejeitado")
    void rejectsMoneyWithoutCurrency() {
        assertRejected(process(List.of(VariableDeclaration.builder().key("a").label("A").format(VariableFormat.MONEY).build())));
        assertRejected(process(List.of(VariableDeclaration.builder().key("a").label("A").format(VariableFormat.MONEY).currency("XYZW").build())));
    }

    @Test
    @DisplayName("KIKWI-063: regex que não compila, longo demais ou em formato não-texto é rejeitado")
    void rejectsInvalidRegex() {
        assertRejected(process(List.of(text("a").validationRegex("([a-z]").build())));
        assertRejected(process(List.of(text("a").validationRegex("a".repeat(513)).build())));
        assertRejected(process(List.of(VariableDeclaration.builder().key("a").label("A").format(VariableFormat.INTEGER)
                .validationRegex("\\d+").build())));
    }

    @Test
    @DisplayName("KIKWI-064: vínculo para variável fora do catálogo é rejeitado")
    void rejectsOrphanBinding() {
        assertRejected(process(List.of(text("a").build()), new VariableBinding("b", true)));
    }

    @Test
    @DisplayName("KIKWI-064: a mesma variável vinculada duas vezes na mesma lista é rejeitada")
    void rejectsDuplicateBinding() {
        assertRejected(process(List.of(text("a").build()), new VariableBinding("a", true), new VariableBinding("a", false)));
    }

    @Test
    @DisplayName("KIKWI-065: inputs em EXECUTABLE_TASK é rejeitado")
    void rejectsInputsOnExecutableTask() {
        ExecutableTaskDefinition task = ExecutableTaskDefinition.builder()
                .id("START").name("Task").executor("anyHandler")
                .outgoing(List.of(new SequenceFlowDefinition("f1", null, null, null, "END", false, false, null, Map.of(), null, null)))
                .variableBindings(new VariableBindings(List.of(new VariableBinding("a", true)), null))
                .build();
        assertRejected(process(List.of(text("a").build()), task));
    }
}
