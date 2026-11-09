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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agregação numérica de variáveis sobre as instâncias que casam com os filtros de um {@link ProcessInstanceQuery}
 * ("quanto dinheiro está parado na etapa X"). Cada {@link Metric} soma/mede uma variável — ou a diferença entre duas
 * ({@code variable - minusVariable}, calculada por instância, ex.: spread venda − compra).
 *
 * <p>Conversão numérica, igual em todas as implementações: números entram como estão; texto entra se estiver no
 * {@link VariableNumberFormat} daquela variável ({@code "45000.00"} em PLAIN, {@code "45.000,00"} em PT_BR); qualquer
 * outra coisa (texto fora do formato, booleano, data, objeto) conta como presente mas não numérica e fica fora da
 * soma. O formato é por variável porque cada tela de um sistema pode gravar de um jeito — e uma diferença (spread)
 * pode subtrair variáveis de formatos distintos. Sem conversão de moeda.
 *
 * @param metrics         1 a {@link #MAX_METRICS} métricas, rótulos únicos
 * @param groupBy         agrupamento; {@link GroupBy#NONE} devolve só os totais
 * @param groupByVariable variável cujo valor define o grupo — obrigatória com {@link GroupBy#VARIABLE}
 * @param maxGroups       teto de grupos devolvidos (os maiores por nº de instâncias)
 * @param numberFormat    formato das variáveis que não estão em {@code variableFormats} (padrão PLAIN)
 * @param variableFormats formato por variável (ex.: o declarado no catálogo); vence {@code numberFormat}
 */
public record VariableAggregation(List<Metric> metrics, GroupBy groupBy, String groupByVariable, int maxGroups,
                                  VariableNumberFormat numberFormat, Map<String, VariableNumberFormat> variableFormats) {

    public static final int MAX_METRICS = 10;
    public static final int MAX_GROUPS = 100;

    public enum GroupBy {
        NONE,
        /** Por nó com token ativo; uma instância em dois nós (paralelo) entra nos dois grupos. */
        ACTIVE_NODE,
        /** Pelo valor de {@code groupByVariable}; instâncias sem a variável formam o grupo {@code null}. */
        VARIABLE
    }

    /**
     * @param label         nome da métrica no resultado (padrão: a expressão)
     * @param variable      variável somada
     * @param minusVariable opcional: subtraída de {@code variable} em cada instância que tem as duas
     */
    public record Metric(String label, String variable, String minusVariable) {

        public Metric {
            requireVariableName(variable, "variable");
            if (minusVariable != null) {
                requireVariableName(minusVariable, "minusVariable");
            }
            if (label == null || label.isBlank()) {
                label = minusVariable == null ? variable : variable + " - " + minusVariable;
            }
        }

        public boolean isDifference() {
            return minusVariable != null;
        }
    }

    public VariableAggregation {
        if (metrics == null || metrics.isEmpty() || metrics.size() > MAX_METRICS) {
            throw new IllegalArgumentException("Informe de 1 a " + MAX_METRICS + " métricas.");
        }
        metrics = List.copyOf(metrics);
        Set<String> labels = new HashSet<>();
        for (Metric metric : metrics) {
            if (!labels.add(metric.label())) {
                throw new IllegalArgumentException("Rótulo de métrica repetido: " + metric.label());
            }
        }
        groupBy = Objects.requireNonNullElse(groupBy, GroupBy.NONE);
        if (groupBy == GroupBy.VARIABLE) {
            requireVariableName(groupByVariable, "groupByVariable");
        } else {
            groupByVariable = null;
        }
        maxGroups = maxGroups <= 0 ? 20 : Math.min(maxGroups, MAX_GROUPS);
        numberFormat = Objects.requireNonNullElse(numberFormat, VariableNumberFormat.PLAIN);
        variableFormats = variableFormats == null ? Map.of() : Map.copyOf(variableFormats);
    }

    public VariableAggregation(List<Metric> metrics, GroupBy groupBy, String groupByVariable, int maxGroups) {
        this(metrics, groupBy, groupByVariable, maxGroups, VariableNumberFormat.PLAIN, Map.of());
    }

    public VariableAggregation(List<Metric> metrics, GroupBy groupBy, String groupByVariable, int maxGroups,
                               VariableNumberFormat numberFormat) {
        this(metrics, groupBy, groupByVariable, maxGroups, numberFormat, Map.of());
    }

    /** Formato com que esta variável é lida. */
    public VariableNumberFormat formatOf(String variable) {
        return variableFormats.getOrDefault(variable, numberFormat);
    }

    public static VariableAggregation totals(List<Metric> metrics) {
        return new VariableAggregation(metrics, GroupBy.NONE, null, 0);
    }

    private static void requireVariableName(String name, String field) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' é obrigatório.");
        }
        // Nome de variável vira caminho de campo no banco: '$' abriria espaço para expressão/operador.
        if (name.contains("$")) {
            throw new IllegalArgumentException("Nome de variável inválido em '" + field + "': " + name);
        }
    }
}
