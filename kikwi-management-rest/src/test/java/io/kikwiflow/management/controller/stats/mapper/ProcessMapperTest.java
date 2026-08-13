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

package io.kikwiflow.management.controller.stats.mapper;

import io.kikwiflow.management.dtos.elements.KKFCallActivityDefinition;
import io.kikwiflow.management.dtos.elements.KKFEventCatcherDefinition;
import io.kikwiflow.management.dtos.elements.KKFEventThrowerDefinition;
import io.kikwiflow.management.dtos.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.dtos.elements.KKFInterruptiveCatchEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFTimerTaskDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.execution.enumerated.CallActivityIterationMode;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;
import io.kikwiflow.model.stats.KKFMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Antes deste teste existir, {@code ProcessMapper.mapNode} lançava {@code IllegalArgumentException} para
 * {@code EventCatcherDefinition}/{@code InterruptiveCatchEventDefinition}/{@code EventThrowerDefinition}/
 * {@code CallActivityDefinition}/{@code TimerTaskDefinition} — qualquer processo contendo um desses nós
 * quebrava {@code GET /pulse/process-definition/{id}/snapshot} (via {@code StatsService}).
 */
@DisplayName("Dado um FlowNodeDefinition dos 5 tipos antes não suportados pelo ProcessMapper")
class ProcessMapperTest {

    @Test
    @DisplayName("EVENT_CATCHER é mapeado para KKFEventCatcherDefinition, carregando as métricas recebidas")
    void mapsEventCatcherDefinition() {
        EventCatcherDefinition eventCatcher = EventCatcherDefinition.builder()
                .id("WAIT_ORDER_PAID")
                .name("Aguardar Pagamento")
                .catchType(CatchType.STANDALONE)
                .providerType(CorrelationProviderType.VARIABLE)
                .providerVariable("orderId")
                .build();
        KKFMetrics metrics = new KKFMetrics(3L, 99.5, 1L);

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(eventCatcher, metrics);

        KKFEventCatcherDefinition kkf = assertInstanceOf(KKFEventCatcherDefinition.class, mapped);
        assertEquals("WAIT_ORDER_PAID", kkf.id());
        assertEquals("EVENT_CATCHER", kkf.type());
        assertEquals(CatchType.STANDALONE, kkf.catchType());
        assertEquals(CorrelationProviderType.VARIABLE, kkf.providerType());
        assertEquals("orderId", kkf.providerVariable());
        assertEquals(metrics, kkf.metrics());
    }

    @Test
    @DisplayName("BOUNDARY_INTERRUPTIVE_CATCH_EVENT é mapeado para KKFInterruptiveCatchEventDefinition, preservando attachedToRef")
    void mapsInterruptiveCatchEventDefinition() {
        InterruptiveCatchEventDefinition catchEvent = InterruptiveCatchEventDefinition.builder()
                .id("CANCEL_CATCH")
                .name("Cancelamento Externo")
                .attachedToRef("COLETAR_DADOS")
                .providerType(CorrelationProviderType.STATIC)
                .staticKey("cancelar-task")
                .build();

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(catchEvent, null);

        KKFInterruptiveCatchEventDefinition kkf = assertInstanceOf(KKFInterruptiveCatchEventDefinition.class, mapped);
        assertEquals("CANCEL_CATCH", kkf.id());
        assertEquals("BOUNDARY_INTERRUPTIVE_CATCH_EVENT", kkf.type());
        assertEquals("COLETAR_DADOS", kkf.attachedToRef());
        assertEquals(CorrelationProviderType.STATIC, kkf.providerType());
        assertEquals("cancelar-task", kkf.staticKey());
    }

    @Test
    @DisplayName("EVENT_THROWER é mapeado para KKFEventThrowerDefinition")
    void mapsEventThrowerDefinition() {
        EventThrowerDefinition eventThrower = EventThrowerDefinition.builder()
                .id("NOTIFY_ORDER_PAID")
                .name("Notificar Pagamento")
                .providerType(CorrelationProviderType.VARIABLE)
                .providerVariable("orderId")
                .build();

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(eventThrower, null);

        KKFEventThrowerDefinition kkf = assertInstanceOf(KKFEventThrowerDefinition.class, mapped);
        assertEquals("NOTIFY_ORDER_PAID", kkf.id());
        assertEquals("EVENT_THROWER", kkf.type());
        assertEquals("orderId", kkf.providerVariable());
    }

    @Test
    @DisplayName("CALL_ACTIVITY_COORDINATOR é mapeado para KKFCallActivityDefinition, carregando as métricas recebidas")
    void mapsCallActivityDefinition() {
        CallActivityDefinition callActivity = CallActivityDefinition.builder()
                .id("CALL_SUBPROCESS")
                .name("Chamar Subprocesso")
                .calledElement("onboarding-subprocess")
                .collectionVariable("items")
                .elementVariable("item")
                .build();
        KKFMetrics metrics = new KKFMetrics(4L, 100.0, 1L);

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(callActivity, metrics);

        KKFCallActivityDefinition kkf = assertInstanceOf(KKFCallActivityDefinition.class, mapped);
        assertEquals("CALL_SUBPROCESS", kkf.id());
        assertEquals("CALL_ACTIVITY_COORDINATOR", kkf.type());
        assertEquals("onboarding-subprocess", kkf.calledElement());
        assertEquals("items", kkf.collectionVariable());
        assertEquals("item", kkf.elementVariable());
        assertEquals(metrics, kkf.metrics());
    }

    @Test
    @DisplayName("CALL_ACTIVITY_COORDINATOR preserva o iterationMode (SEQUENTIAL vs PARALLEL)")
    void mapsCallActivityDefinitionIterationMode() {
        CallActivityDefinition callActivity = CallActivityDefinition.builder()
                .id("CALL_SUBPROCESS")
                .name("Chamar Subprocesso")
                .calledElement("onboarding-subprocess")
                .collectionVariable("items")
                .elementVariable("item")
                .iterationMode(CallActivityIterationMode.SEQUENTIAL)
                .build();

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(callActivity, null);

        KKFCallActivityDefinition kkf = assertInstanceOf(KKFCallActivityDefinition.class, mapped);
        assertEquals(CallActivityIterationMode.SEQUENTIAL, kkf.iterationMode());
    }

    @Test
    @DisplayName("TIMER_TASK é mapeado para KKFTimerTaskDefinition, carregando as métricas recebidas")
    void mapsTimerTaskDefinition() {
        TimerTaskDefinition timerTask = new TimerTaskDefinition(
                "WAIT_24H", "Aguardar 24h", "TIMER_TASK", null, false, true,
                java.util.List.of(), TimeProviderType.STATIC, null, null, "PT24H",
                java.util.List.of(), java.util.Map.of(), null);
        KKFMetrics metrics = new KKFMetrics(7L, 100.0, 0L);

        KKFFlowNodeDefinition mapped = ProcessMapper.mapNode(timerTask, metrics);

        KKFTimerTaskDefinition kkf = assertInstanceOf(KKFTimerTaskDefinition.class, mapped);
        assertEquals("WAIT_24H", kkf.id());
        assertEquals("TIMER_TASK", kkf.type());
        assertEquals(TimeProviderType.STATIC, kkf.providerType());
        assertEquals("PT24H", kkf.staticValue());
        assertEquals(metrics, kkf.metrics());
    }
}
