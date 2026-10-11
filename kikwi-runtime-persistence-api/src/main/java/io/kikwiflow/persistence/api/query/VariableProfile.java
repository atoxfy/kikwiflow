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

package io.kikwiflow.persistence.api.query;

import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * O que as instâncias realmente gravam, por chave de variável — resultado de
 * {@link ProcessInstanceQuery#profileVariables(int)}. Só estatística: nenhum valor sai daqui.
 *
 * @param sampledInstances instâncias examinadas (as mais recentes que casam com os filtros, até o tamanho da amostra)
 * @param variables        uma linha por chave, das mais preenchidas para as menos
 */
public record VariableProfile(long sampledInstances, List<KeyProfile> variables) {

    public static final int MAX_SAMPLE = 5_000;

    /**
     * @param key           chave da variável
     * @param presentCount  instâncias com valor não nulo
     * @param types         tipo observado → instâncias (nomes de tipo BSON: string, int, long, double, decimal,
     *                      bool, date, object, array, other)
     * @param plainNumeric  instâncias cujo valor é número em {@link VariableNumberFormat#PLAIN}
     * @param ptBrNumeric   instâncias cujo valor é número em {@link VariableNumberFormat#PT_BR}
     */
    public record KeyProfile(String key, long presentCount, Map<String, Long> types, long plainNumeric, long ptBrNumeric) {

        /** Formato que lê mais valores como número, ou {@code null} se nenhum lê. Empate fica com PLAIN. */
        public VariableNumberFormat suggestedNumberFormat() {
            if (plainNumeric == 0 && ptBrNumeric == 0) {
                return null;
            }
            return ptBrNumeric > plainNumeric ? VariableNumberFormat.PT_BR : VariableNumberFormat.PLAIN;
        }
    }

    /** Nome de tipo no mesmo vocabulário do {@code $type} do MongoDB, para as implementações em memória. */
    public static String typeName(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) return "int";
        if (value instanceof Long) return "long";
        if (value instanceof Double || value instanceof Float) return "double";
        if (value instanceof BigDecimal) return "decimal";
        if (value instanceof Boolean) return "bool";
        if (value instanceof Date || value instanceof Temporal) return "date";
        if (value instanceof Map<?, ?>) return "object";
        if (value instanceof Collection<?> || (value != null && value.getClass().isArray())) return "array";
        return "other";
    }
}
