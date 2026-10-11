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

import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.api.query.VariableAggregation;
import io.kikwiflow.persistence.api.query.VariableAggregation.GroupBy;
import io.kikwiflow.persistence.api.query.VariableAggregation.Metric;
import io.kikwiflow.persistence.api.query.VariableAggregationResult;
import io.kikwiflow.persistence.api.query.VariableAggregationResult.MetricValue;
import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;
import io.kikwiflow.persistence.api.query.VariableProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Agregação de variáveis (in-memory)")
class InMemoryVariableAggregationTest {

    private static final Metric COMPRA = new Metric(null, "compra.valor", null);
    private static final Metric SPREAD = new Metric("spread", "venda.valor", "compra.valor");

    private InMemoryKikwiEngineRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryKikwiEngineRepository(new ArrayDeque<OutboxEventEntity>());
        // Valores como o vaapty grava (texto decimal) misturados com números e lixo.
        seed("pi-1", ProcessInstanceStatus.ACTIVE, Map.of("EM_ESTOQUE", 1), Map.of("compra.valor", "40000.00", "venda.valor", "45000.00", "loja", "A"));
        seed("pi-2", ProcessInstanceStatus.ACTIVE, Map.of("EM_ESTOQUE", 1), Map.of("compra.valor", 30000, "venda.valor", new BigDecimal("33500.50"), "loja", "B"));
        seed("pi-3", ProcessInstanceStatus.ACTIVE, Map.of("EM_ESTOQUE", 1), Map.of("compra.valor", "20.000,00", "loja", "A"));
        seed("pi-4", ProcessInstanceStatus.ACTIVE, Map.of("VENDIDO", 1, "EM_ESTOQUE", 0), Map.of("compra.valor", 10000.5d, "loja", "A"));
        seed("pi-5", ProcessInstanceStatus.COMPLETED, Map.of(), Map.of("compra.valor", "99999"));
    }

    @Nested
    @DisplayName("Dado instâncias em Em estoque")
    class Totals {

        @Test
        @DisplayName("Então soma número e texto decimal, e separa ausente de não numérico")
        void sumsAndCountsDataQuality() {
            VariableAggregationResult result = repository.createProcessInstanceQuery()
                    .activeNodeId("EM_ESTOQUE")
                    .statusIn(List.of(ProcessInstanceStatus.ACTIVE))
                    .aggregate(VariableAggregation.totals(List.of(COMPRA)));

            MetricValue compra = result.totals().metrics().get("compra.valor");
            assertEquals(3, result.totals().instanceCount());
            assertEquals(0, new BigDecimal("70000").compareTo(compra.sum()));
            assertEquals(2, compra.numericCount());
            assertEquals(1, compra.nonNumericCount(), "\"20.000,00\" não é decimal simples");
            assertEquals(0, new BigDecimal("35000").compareTo(compra.avg()));
            assertEquals(0, new BigDecimal("30000").compareTo(compra.min()));
            assertEquals(0, new BigDecimal("40000.00").compareTo(compra.max()));
        }

        @Test
        @DisplayName("Então o spread só conta instâncias com as duas variáveis numéricas")
        void differencePerInstance() {
            VariableAggregationResult result = repository.createProcessInstanceQuery()
                    .activeNodeId("EM_ESTOQUE")
                    .aggregate(VariableAggregation.totals(List.of(SPREAD)));

            MetricValue spread = result.totals().metrics().get("spread");
            assertEquals(0, new BigDecimal("8500.50").compareTo(spread.sum()), "5000.00 + 3500.50");
            assertEquals(2, spread.numericCount());
            assertEquals(2, spread.presentCount(), "pi-3 não tem venda.valor: ausente, não 'não numérico'");
        }

        @Test
        @DisplayName("Então nenhuma instância casando devolve soma zero e média vazia")
        void emptySelection() {
            VariableAggregationResult result = repository.createProcessInstanceQuery()
                    .activeNodeId("NAO_EXISTE")
                    .aggregate(VariableAggregation.totals(List.of(COMPRA)));

            assertEquals(0, result.totals().instanceCount());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.totals().metrics().get("compra.valor").sum()));
            assertNull(result.totals().metrics().get("compra.valor").avg());
        }
    }

    @Nested
    @DisplayName("Dado um agrupamento")
    class Grouping {

        @Test
        @DisplayName("Por etapa: só nós com token ativo, maiores primeiro")
        void byActiveNode() {
            VariableAggregationResult result = repository.createProcessInstanceQuery()
                    .statusIn(List.of(ProcessInstanceStatus.ACTIVE))
                    .aggregate(new VariableAggregation(List.of(COMPRA), GroupBy.ACTIVE_NODE, null, 10));

            assertEquals(List.of("EM_ESTOQUE", "VENDIDO"), result.groups().stream().map(g -> g.key()).toList());
            assertEquals(3, result.groups().get(0).instanceCount(), "pi-4 tem EM_ESTOQUE=0: não conta");
            assertEquals(0, new BigDecimal("10000.5").compareTo(result.groups().get(1).metrics().get("compra.valor").sum()));
            assertEquals(4, result.totals().instanceCount());
        }

        @Test
        @DisplayName("Por variável, respeitando maxGroups")
        void byVariableTruncated() {
            VariableAggregationResult result = repository.createProcessInstanceQuery()
                    .statusIn(List.of(ProcessInstanceStatus.ACTIVE))
                    .aggregate(new VariableAggregation(List.of(COMPRA), GroupBy.VARIABLE, "loja", 1));

            assertEquals(1, result.groups().size());
            assertEquals("A", result.groups().get(0).key());
            assertEquals(3, result.groups().get(0).instanceCount());
            assertTrue(result.groupsTruncated());
        }
    }

    @Test
    @DisplayName("Em PT_BR, \"20.000,00\" entra e \"40000.00\" fica de fora (leitura estrita, sem palpite)")
    void ptBrFormat() {
        VariableAggregationResult result = repository.createProcessInstanceQuery()
                .activeNodeId("EM_ESTOQUE")
                .aggregate(new VariableAggregation(List.of(COMPRA), GroupBy.NONE, null, 0, VariableNumberFormat.PT_BR));

        MetricValue compra = result.totals().metrics().get("compra.valor");
        assertEquals(0, new BigDecimal("50000").compareTo(compra.sum()), "30000 (número) + 20.000,00");
        assertEquals(1, compra.nonNumericCount());
    }

    @Test
    @DisplayName("Spread entre variáveis de formatos diferentes: cada lado é lido no seu formato")
    void perVariableFormats() {
        VariableAggregationResult result = repository.createProcessInstanceQuery()
                .activeNodeId("EM_ESTOQUE")
                .aggregate(new VariableAggregation(List.of(SPREAD), GroupBy.NONE, null, 0, VariableNumberFormat.PLAIN,
                        Map.of("compra.valor", VariableNumberFormat.PT_BR)));

        MetricValue spread = result.totals().metrics().get("spread");
        assertEquals(0, new BigDecimal("3500.50").compareTo(spread.sum()), "pi-1: \"40000.00\" não é PT_BR; pi-2: 33500.50 - 30000");
        assertEquals(1, spread.numericCount());
        assertEquals(2, spread.presentCount());
    }

    @Nested
    @DisplayName("Dado o perfil das variáveis")
    class Profile {

        @Test
        @DisplayName("Então lista chaves por preenchimento, com tipos e leitura numérica nos dois formatos")
        void profilesKeys() {
            VariableProfile profile = repository.createProcessInstanceQuery().profileVariables(100);

            assertEquals(5, profile.sampledInstances());
            assertEquals(List.of("compra.valor", "loja", "venda.valor"),
                    profile.variables().stream().map(VariableProfile.KeyProfile::key).toList());
            VariableProfile.KeyProfile compra = profile.variables().get(0);
            assertEquals(5, compra.presentCount());
            assertEquals(Map.of("string", 3L, "int", 1L, "double", 1L), compra.types());
            assertEquals(4, compra.plainNumeric());
            assertEquals(4, compra.ptBrNumeric());
            assertNull(profile.variables().get(1).suggestedNumberFormat(), "loja não é número em formato nenhum");
        }

        @Test
        @DisplayName("Então respeita filtros e o tamanho da amostra")
        void respectsSample() {
            assertEquals(2, repository.createProcessInstanceQuery()
                    .statusIn(List.of(ProcessInstanceStatus.ACTIVE)).profileVariables(2).sampledInstances());
        }
    }

    @Test
    @DisplayName("Nome de variável com '$' é recusado (vira caminho de campo no banco)")
    void rejectsOperatorLikeNames() {
        assertThrows(IllegalArgumentException.class, () -> new Metric(null, "$where", null));
    }

    private void seed(String id, ProcessInstanceStatus status, Map<String, Integer> activeNodes, Map<String, Object> values) {
        Map<String, ProcessVariable> variables = new LinkedHashMap<>();
        values.forEach((k, v) -> variables.put(k, new ProcessVariable(k, v)));
        repository.saveProcessInstance(ProcessInstance.builder()
                .id(id)
                .businessKey("bk-" + id)
                .status(status)
                .processDefinitionId("def-1")
                .startedAt(Instant.now())
                .activeNodes(activeNodes)
                .variables(variables)
                .build());
    }
}
