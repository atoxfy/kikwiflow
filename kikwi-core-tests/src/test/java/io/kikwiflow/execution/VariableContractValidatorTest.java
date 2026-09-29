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

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.variable.VariableContractValidator;
import io.kikwiflow.variable.VariableValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tabela de verdade do {@link VariableContractValidator}: para cada {@link VariableFormat}, quais valores são
 * aceitos — incluindo as formas em que o dado chega de JSON (Integer/Double/String) e do Mongo (Date).
 */
@DisplayName("Dado um vínculo opcional a uma variável de cada formato")
class VariableContractValidatorTest {

    private final VariableContractValidator validator = new VariableContractValidator();

    private static final List<VariableOption> OPTIONS = List.of(new VariableOption("A", "Opção A"), new VariableOption("B", "Opção B"));

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(VariableFormat.SHORT_TEXT, "abc", null),
                Arguments.of(VariableFormat.SHORT_TEXT, 10, VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.LONG_TEXT, "linha 1\nlinha 2", null),
                Arguments.of(VariableFormat.INTEGER, 42, null),
                Arguments.of(VariableFormat.INTEGER, 42L, null),
                Arguments.of(VariableFormat.INTEGER, "42", null),
                Arguments.of(VariableFormat.INTEGER, 42.0, null),
                Arguments.of(VariableFormat.INTEGER, 42.5, VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.INTEGER, "4x", VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.DECIMAL, 10.25, null),
                Arguments.of(VariableFormat.DECIMAL, new BigDecimal("10.25"), null),
                Arguments.of(VariableFormat.DECIMAL, "10.25", null),
                Arguments.of(VariableFormat.DECIMAL, Double.NaN, VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.MONEY, 99, null),
                Arguments.of(VariableFormat.MONEY, "R$ 10", VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.BOOLEAN, false, null),
                Arguments.of(VariableFormat.BOOLEAN, "true", VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.DATE, LocalDate.of(2026, 9, 25), null),
                Arguments.of(VariableFormat.DATE, "2026-09-25", null),
                Arguments.of(VariableFormat.DATE, new Date(), null),
                Arguments.of(VariableFormat.DATE, "25/09/2026", VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.DATETIME, OffsetDateTime.now(), null),
                Arguments.of(VariableFormat.DATETIME, "2026-09-25T10:15:30-03:00", null),
                Arguments.of(VariableFormat.DATETIME, "2026-09-25", VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.SINGLE_SELECT, "A", null),
                Arguments.of(VariableFormat.SINGLE_SELECT, "C", VariableValidationError.INVALID_OPTION),
                Arguments.of(VariableFormat.SINGLE_SELECT, List.of("A"), VariableValidationError.INVALID_FORMAT),
                Arguments.of(VariableFormat.MULTI_SELECT, List.of("A", "B"), null),
                Arguments.of(VariableFormat.MULTI_SELECT, List.of("A", "C"), VariableValidationError.INVALID_OPTION),
                Arguments.of(VariableFormat.MULTI_SELECT, "A", VariableValidationError.INVALID_FORMAT)
        );
    }

    @ParameterizedTest(name = "{0} com valor {1} → {2}")
    @MethodSource("cases")
    @DisplayName("Quando um valor é informado, então o formato é checado")
    void checksFormat(VariableFormat format, Object value, String expectedCode) {
        ProcessDefinition definition = definitionWith(VariableDeclaration.builder()
                .key("v").label("V").format(format)
                .options(format.isSelect() ? OPTIONS : null)
                .currency(format == VariableFormat.MONEY ? "BRL" : null)
                .build());

        List<VariableValidationError> errors = validator.validate(List.of(new VariableBinding("v", false)),
                definition, Map.of("v", new ProcessVariable("v", value)));

        assertEquals(expectedCode, errors.isEmpty() ? null : errors.get(0).code());
    }

    static Stream<Arguments> emptyValues() {
        return Stream.of(Arguments.of((Object) null), Arguments.of("   "), Arguments.of(List.of()));
    }

    @ParameterizedTest(name = "valor vazio {0}")
    @MethodSource("emptyValues")
    @DisplayName("Quando o valor está vazio, então só falha se o vínculo for required")
    void emptyValueOnlyFailsWhenRequired(Object value) {
        ProcessDefinition definition = definitionWith(VariableDeclaration.builder()
                .key("v").label("V").format(VariableFormat.MULTI_SELECT).options(OPTIONS).build());
        Map<String, ProcessVariable> variables = new java.util.HashMap<>();
        variables.put("v", new ProcessVariable("v", value));

        assertEquals(List.of(), validator.validate(List.of(new VariableBinding("v", null)), definition, variables));
        assertEquals(VariableValidationError.REQUIRED,
                validator.validate(List.of(new VariableBinding("v", true)), definition, variables).get(0).code());
    }

    private static ProcessDefinition definitionWith(VariableDeclaration declaration) {
        return ProcessDefinition.builder()
                .key("validator-test")
                .variableDeclarations(List.of(declaration))
                .build();
    }
}
