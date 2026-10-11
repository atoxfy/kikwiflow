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

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de {@link ProcessInstanceQuery#aggregate(VariableAggregation)}.
 *
 * @param totals          todas as instâncias filtradas, cada uma contada uma vez (mesmo com {@code ACTIVE_NODE})
 * @param groups          grupos em ordem decrescente de instâncias; vazio com {@code GroupBy.NONE}
 * @param groupsTruncated havia mais grupos que {@code maxGroups}
 */
public record VariableAggregationResult(Group totals, List<Group> groups, boolean groupsTruncated) {

    /**
     * @param key           valor do grupo (id do nó, valor da variável) — {@code null} nos totais
     * @param instanceCount instâncias no grupo
     * @param metrics       por rótulo de métrica, na ordem pedida
     */
    public record Group(Object key, long instanceCount, Map<String, MetricValue> metrics) {
    }

    /**
     * @param sum          soma dos valores numéricos ({@code 0} se nenhum)
     * @param avg          média dos valores numéricos ({@code null} se nenhum)
     * @param min          menor valor ({@code null} se nenhum)
     * @param max          maior valor ({@code null} se nenhum)
     * @param numericCount instâncias que entraram na conta
     * @param presentCount instâncias com a variável (as duas, numa diferença) preenchida, numérica ou não
     */
    public record MetricValue(BigDecimal sum, BigDecimal avg, BigDecimal min, BigDecimal max,
                              long numericCount, long presentCount) {

        /** Presente, mas não convertível para número (ex.: {@code "45.000,00"}) — sinal de dado a corrigir. */
        public long nonNumericCount() {
            return Math.max(0, presentCount - numericCount);
        }
    }

    /**
     * Acumulador em memória de uma métrica (uso das implementações não-Mongo). Recebe, por instância, se o valor
     * estava presente e o valor numérico já convertido (ou {@code null}).
     */
    public static final class Accumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal min;
        private BigDecimal max;
        private long numeric;
        private long present;

        public void add(boolean isPresent, BigDecimal value) {
            if (isPresent) {
                present++;
            }
            if (value == null) {
                return;
            }
            numeric++;
            sum = sum.add(value);
            min = min == null || value.compareTo(min) < 0 ? value : min;
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }

        public MetricValue toValue() {
            BigDecimal avg = numeric == 0 ? null : sum.divide(BigDecimal.valueOf(numeric), MathContext.DECIMAL128);
            return new MetricValue(sum, avg, min, max, numeric, present);
        }
    }

    /** Ordena por instâncias (desc) e corta em {@code maxGroups}. */
    public static VariableAggregationResult of(Group totals, List<Group> groups, int maxGroups) {
        List<Group> sorted = new ArrayList<>(groups);
        sorted.sort(Comparator.comparingLong(Group::instanceCount).reversed());
        boolean truncated = sorted.size() > maxGroups;
        return new VariableAggregationResult(totals, truncated ? List.copyOf(sorted.subList(0, maxGroups)) : List.copyOf(sorted), truncated);
    }

    public static Map<String, MetricValue> emptyMetrics(List<VariableAggregation.Metric> metrics) {
        Map<String, MetricValue> values = new LinkedHashMap<>();
        metrics.forEach(m -> values.put(m.label(), new Accumulator().toValue()));
        return values;
    }
}
