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

package io.kikwiflow.management.service;

import io.kikwiflow.management.dtos.KKFProcessStats;
import io.kikwiflow.management.dtos.elements.KKFCallActivityDefinition;
import io.kikwiflow.management.dtos.elements.KKFEventCatcherDefinition;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Antes desta correção, um processo implantado contendo um {@code EVENT_CATCHER} quebrava
 * {@code GET /pulse/process-definition/{id}/snapshot} com {@code IllegalArgumentException} — este teste é o
 * caso de regressão para isso, e cobre que o nó também recebe métricas (é um {@code WaitState}, como
 * {@code EXTERNAL_TASK}), não {@code null}.
 */
@DisplayName("Dado um ProcessDefinition com um EVENT_CATCHER, ao montar o snapshot do Pulse")
class StatsServiceTest {

    @Test
    @DisplayName("buildProcessSnapshot não lança exceção e mapeia o EVENT_CATCHER com métricas não nulas")
    void buildProcessSnapshotMapsEventCatcherWithMetrics() {
        EventCatcherDefinition eventCatcher = EventCatcherDefinition.builder()
                .id("WAIT_ORDER_PAID")
                .name("Aguardar Pagamento")
                .catchType(CatchType.STANDALONE)
                .providerType(CorrelationProviderType.VARIABLE)
                .providerVariable("orderId")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-1")
                .key("order-process")
                .name("Processo de Pedido")
                .flowNodes(Map.of("WAIT_ORDER_PAID", eventCatcher))
                .defaultStartPoint("WAIT_ORDER_PAID")
                .build();

        QueryRepository queryRepository = mock(QueryRepository.class);
        when(queryRepository.findProcessDefinitionById("def-1")).thenReturn(Optional.of(definition));
        when(queryRepository.getProcessMacroMetrics("def-1")).thenReturn(new KKFMetrics(1L, 100.0, 0L));
        when(queryRepository.getMetricsByNodeForProcessDefinition("def-1")).thenReturn(Collections.emptyMap());

        StatsService statsService = new StatsService(queryRepository);

        KKFProcessStats stats = assertDoesNotThrow(() -> statsService.buildProcessSnapshot("def-1"));

        KKFEventCatcherDefinition mappedNode = assertInstanceOf(
                KKFEventCatcherDefinition.class, stats.flowNodes().get("WAIT_ORDER_PAID"));
        assertNotNull(mappedNode.metrics(), "EVENT_CATCHER é um WaitState — deveria receber métricas, não null.");
        assertEquals(new KKFMetrics(0L, 100.00, 0L), mappedNode.metrics());
    }

    /**
     * A coordenadora de um CALL_ACTIVITY_COORDINATOR é materializada como ExecutableTask (mesma coleção
     * agregada por getMetricsByNodeForProcessDefinition), então "quantas instâncias estão paradas aqui" existe
     * de verdade em nodeMetrics — mas até esta correção {@code StatsService} descartava esse valor (só
     * EXECUTABLE_TASK/EXTERNAL_TASK/EVENT_CATCHER recebiam o metrics computado; qualquer outro tipo, incluindo
     * CALL_ACTIVITY_COORDINATOR, recebia sempre null, mesmo com dados reais disponíveis).
     */
    @Test
    @DisplayName("buildProcessSnapshot mapeia CALL_ACTIVITY_COORDINATOR com as métricas reais de nodeMetrics, não null")
    void buildProcessSnapshotMapsCallActivityCoordinatorWithMetrics() {
        CallActivityDefinition callActivity = CallActivityDefinition.builder()
                .id("CALL_ATIVAR_PRODUTOS")
                .name("Ativar Produtos")
                .calledElement("product-activation-process")
                .collectionVariable("produtos")
                .elementVariable("produto")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-1")
                .key("onboarding-ativacao-produtos-paralelo")
                .name("Ativação de Produtos - Paralelo")
                .flowNodes(Map.of("CALL_ATIVAR_PRODUTOS", callActivity))
                .defaultStartPoint("CALL_ATIVAR_PRODUTOS")
                .build();

        QueryRepository queryRepository = mock(QueryRepository.class);
        when(queryRepository.findProcessDefinitionById("def-1")).thenReturn(Optional.of(definition));
        when(queryRepository.getProcessMacroMetrics("def-1")).thenReturn(new KKFMetrics(1L, 100.0, 0L));
        when(queryRepository.getMetricsByNodeForProcessDefinition("def-1"))
                .thenReturn(Map.of("CALL_ATIVAR_PRODUTOS", new KKFMetrics(3L, 100.0, 1L)));

        StatsService statsService = new StatsService(queryRepository);

        KKFProcessStats stats = statsService.buildProcessSnapshot("def-1");

        KKFCallActivityDefinition mappedNode = assertInstanceOf(
                KKFCallActivityDefinition.class, stats.flowNodes().get("CALL_ATIVAR_PRODUTOS"));
        assertEquals(new KKFMetrics(3L, 100.0, 1L), mappedNode.metrics());
    }
}
