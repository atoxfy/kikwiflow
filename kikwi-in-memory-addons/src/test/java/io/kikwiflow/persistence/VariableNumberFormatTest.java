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

package io.kikwiflow.persistence;

import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tabela da leitura estrita de texto numérico. A mesma regra roda no pipeline Mongo ({@code $regexMatch} com
 * {@link VariableNumberFormat#regex()}): mudar um caso aqui é mudar o contrato das duas implementações.
 */
@DisplayName("Formato numérico de variáveis gravadas como texto")
class VariableNumberFormatTest {

    @ParameterizedTest(name = "{0} \"{1}\" -> {2}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "PLAIN | 45000.00     | 45000.00",
            "PLAIN | -12.5        | -12.5",
            "PLAIN | ' 7 '        | 7",
            "PLAIN | 45.000,00    | null",
            "PLAIN | 1e5          | null",
            "PLAIN | R$ 10.00     | null",
            "PT_BR | 45.000,00    | 45000.00",
            "PT_BR | 45000,00     | 45000.00",
            "PT_BR | R$ 1.234,5   | 1234.5",
            "PT_BR | -R$ 10,00    | -10.00",
            "PT_BR | 1.234.567,89 | 1234567.89",
            "PT_BR | 7            | 7",
            "PT_BR | 45.000       | 45000",
            "PT_BR | 45000.00     | null",
            "PT_BR | 1.23         | null",
            "PT_BR | 12.3456,7    | null",
            "PT_BR | abc          | null",
            "AUTO  | 45000.00     | 45000.00",
            "AUTO  | 45.000,00    | 45000.00",
            "AUTO  | 45.000       | 45.000",
            "AUTO  | abc          | null",
    })
    void parsesStrictly(String format, String text, String expected) {
        BigDecimal parsed = VariableNumberFormat.valueOf(format.trim()).parse(text);
        if (expected == null) {
            assertNull(parsed);
        } else {
            assertEquals(new BigDecimal(expected), parsed);
        }
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(nullValues = "null", value = {"PT_BR,PT_BR", "pt-br,PT_BR", "plain,PLAIN", "outro,null"})
    @DisplayName("Valor da propriedade do catálogo")
    void readsCatalogProperty(String value, VariableNumberFormat expected) {
        assertEquals(expected, VariableNumberFormat.fromCatalog(value));
    }
}
