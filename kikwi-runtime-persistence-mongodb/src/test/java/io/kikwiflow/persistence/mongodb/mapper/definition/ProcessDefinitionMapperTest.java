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

package io.kikwiflow.persistence.mongodb.mapper.definition;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateSegment;
import io.kikwiflow.model.execution.enumerated.CallActivityIterationMode;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.enumerated.TemplateSegmentType;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Antes deste teste, {@code ProcessDefinitionMapper} não tinha nenhum round-trip verificado para
 * {@code EventCatcherDefinition}/{@code InterruptiveCatchEventDefinition} — o switch de {@code toDocument}
 * caía silenciosamente no {@code default} (perdendo todos os campos específicos) e {@code fromDocMappers} não
 * conhecia a classe, o que faria {@code fromDocument} lançar {@code IllegalArgumentException} em qualquer
 * deploy real via MongoDB.
 */
class ProcessDefinitionMapperTest {

    @Test
    void roundTripsEventCatcherGroupTemplateFields() {
        EventCatcherDefinition eventCatcher = EventCatcherDefinition.builder()
                .id("WAIT_PRODUCTS")
                .name("Aguardar Produtos")
                .catchType(CatchType.GROUP)
                .providerType(CorrelationProviderType.TEMPLATE)
                .matchPolicy(MatchPolicy.ALL)
                .correlationTemplates(List.of(new CorrelationTemplateDefinition(
                        List.of(new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "CONTA_"),
                                new CorrelationTemplateSegment(TemplateSegmentType.VARIABLE, "cpf"),
                                new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "_EFETIVADA")),
                        List.of(new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "Efetivação da Conta")))))
                .boundaryEventIds(List.of("TIMER_SLA_TIMEOUT"))
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-1").key("proc-key").version(1)
                .flowNodes(Map.of(eventCatcher.id(), eventCatcher))
                .build();

        Document doc = ProcessDefinitionMapper.toDocument(definition);
        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(doc);

        FlowNodeDefinition restoredNode = restored.flowNodes().get("WAIT_PRODUCTS");
        EventCatcherDefinition restoredCatcher = assertInstanceOf(EventCatcherDefinition.class, restoredNode);

        assertEquals(CatchType.GROUP, restoredCatcher.catchType());
        assertEquals(CorrelationProviderType.TEMPLATE, restoredCatcher.providerType());
        assertEquals(MatchPolicy.ALL, restoredCatcher.matchPolicy());
        assertEquals(List.of("TIMER_SLA_TIMEOUT"), restoredCatcher.boundaryEventIds());
        assertEquals(1, restoredCatcher.correlationTemplates().size());
        assertEquals("CONTA_", restoredCatcher.correlationTemplates().get(0).keySegments().get(0).value());
        assertEquals("cpf", restoredCatcher.correlationTemplates().get(0).keySegments().get(1).value());
        assertEquals(TemplateSegmentType.VARIABLE, restoredCatcher.correlationTemplates().get(0).keySegments().get(1).type());
        assertEquals("Efetivação da Conta",
                restoredCatcher.correlationTemplates().get(0).displayNameSegments().get(0).value());
    }

    @Test
    void roundTripsInterruptiveCatchEventFields() {
        InterruptiveCatchEventDefinition catchEvent = InterruptiveCatchEventDefinition.builder()
                .id("CANCEL_CATCH")
                .name("Cancelamento Externo")
                .attachedToRef("COLETAR_DADOS")
                .providerType(CorrelationProviderType.VARIABLE)
                .providerVariable("taskId")
                .keyPrefix("CANCELAR_TASK_")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-2").key("proc-key-2").version(1)
                .flowNodes(Map.of(catchEvent.id(), catchEvent))
                .build();

        Document doc = ProcessDefinitionMapper.toDocument(definition);
        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(doc);

        FlowNodeDefinition restoredNode = restored.flowNodes().get("CANCEL_CATCH");
        InterruptiveCatchEventDefinition restoredCatchEvent = assertInstanceOf(InterruptiveCatchEventDefinition.class, restoredNode);

        assertEquals("COLETAR_DADOS", restoredCatchEvent.attachedToRef());
        assertEquals(CorrelationProviderType.VARIABLE, restoredCatchEvent.providerType());
        assertEquals("taskId", restoredCatchEvent.providerVariable());
        assertEquals("CANCELAR_TASK_", restoredCatchEvent.keyPrefix());
        assertNull(restoredCatchEvent.correlationTemplates());
    }

    @Test
    void roundTripsExternalTaskWithBoundaryCatchEventReference() {
        ExternalTaskDefinition externalTask = ExternalTaskDefinition.builder()
                .id("COLETAR_DADOS")
                .name("Coletar Dados")
                .boundaryEventIds(List.of("CANCEL_CATCH"))
                .build();

        InterruptiveCatchEventDefinition catchEvent = InterruptiveCatchEventDefinition.builder()
                .id("CANCEL_CATCH")
                .attachedToRef("COLETAR_DADOS")
                .providerType(CorrelationProviderType.STATIC)
                .staticKey("cancelar-task-15649234")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-3").key("proc-key-3").version(1)
                .flowNodes(Map.of(externalTask.id(), externalTask, catchEvent.id(), catchEvent))
                .build();

        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(ProcessDefinitionMapper.toDocument(definition));

        ExternalTaskDefinition restoredTask = assertInstanceOf(ExternalTaskDefinition.class, restored.flowNodes().get("COLETAR_DADOS"));
        assertEquals(List.of("CANCEL_CATCH"), restoredTask.boundaryEventIds());

        InterruptiveCatchEventDefinition restoredCatchEvent =
                assertInstanceOf(InterruptiveCatchEventDefinition.class, restored.flowNodes().get("CANCEL_CATCH"));
        assertEquals("cancelar-task-15649234", restoredCatchEvent.staticKey());
    }

    @Test
    void roundTripsTimerTaskFields() {
        TimerTaskDefinition timerTask = TimerTaskDefinition.builder()
                .id("WAIT_SLA")
                .name("Aguardar SLA")
                .providerType(TimeProviderType.VARIABLE)
                .providerVariable("slaDuration")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-4").key("proc-key-4").version(1)
                .flowNodes(Map.of(timerTask.id(), timerTask))
                .build();

        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(ProcessDefinitionMapper.toDocument(definition));

        TimerTaskDefinition restoredTimerTask = assertInstanceOf(TimerTaskDefinition.class, restored.flowNodes().get("WAIT_SLA"));
        assertEquals(TimeProviderType.VARIABLE, restoredTimerTask.providerType());
        assertEquals("slaDuration", restoredTimerTask.providerVariable());
    }

    /**
     * Antes deste teste, {@code CallActivityDefinition} não tinha nenhuma entrada em {@code ProcessDefinitionMapper}
     * — {@code toDocument} descartava todos os campos específicos do nó e {@code fromDocument} lançava
     * {@code IllegalArgumentException} para qualquer processo com um nó {@code CALL_ACTIVITY_COORDINATOR}, em
     * qualquer deploy real via MongoDB (ver {@code CallActivityDefinitionMapper}).
     */
    @Test
    void roundTripsCallActivitySequentialFields() {
        CallActivityDefinition callActivity = CallActivityDefinition.builder()
                .id("CALL_CHILD")
                .name("Chamar Subprocesso")
                .calledElement("onboarding-subprocess")
                .collectionVariable("documents")
                .elementVariable("document")
                .iterationMode(CallActivityIterationMode.SEQUENTIAL)
                .boundaryEventIds(List.of("TIMER_SLA_TIMEOUT"))
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-5").key("proc-key-5").version(1)
                .flowNodes(Map.of(callActivity.id(), callActivity))
                .build();

        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(ProcessDefinitionMapper.toDocument(definition));

        CallActivityDefinition restoredCallActivity =
                assertInstanceOf(CallActivityDefinition.class, restored.flowNodes().get("CALL_CHILD"));
        assertEquals("onboarding-subprocess", restoredCallActivity.calledElement());
        assertEquals("documents", restoredCallActivity.collectionVariable());
        assertEquals("document", restoredCallActivity.elementVariable());
        assertEquals(CallActivityIterationMode.SEQUENTIAL, restoredCallActivity.iterationMode());
        assertEquals(List.of("TIMER_SLA_TIMEOUT"), restoredCallActivity.boundaryEventIds());
    }

    @Test
    void roundTripsCallActivityWithNullIterationModeAsParallel() {
        CallActivityDefinition callActivity = CallActivityDefinition.builder()
                .id("CALL_CHILD")
                .name("Chamar Subprocesso")
                .calledElement("onboarding-subprocess")
                .build();

        ProcessDefinition definition = ProcessDefinition.builder()
                .id("def-6").key("proc-key-6").version(1)
                .flowNodes(Map.of(callActivity.id(), callActivity))
                .build();

        ProcessDefinition restored = ProcessDefinitionMapper.fromDocument(ProcessDefinitionMapper.toDocument(definition));

        CallActivityDefinition restoredCallActivity =
                assertInstanceOf(CallActivityDefinition.class, restored.flowNodes().get("CALL_CHILD"));
        assertNull(restoredCallActivity.iterationMode(),
                "iterationMode ausente deve sobreviver ao round-trip como null — tratado como PARALLEL em todo o motor.");
    }
}
