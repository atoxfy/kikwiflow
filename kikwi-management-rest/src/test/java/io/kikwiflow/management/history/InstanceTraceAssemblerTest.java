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
package io.kikwiflow.management.history;

import io.kikwiflow.model.event.CriticalEvent;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Dado o histórico de uma instância de KYC")
class InstanceTraceAssemblerTest {

    private static final String PI = "pi-1";
    private static final String DEF = "def-1";
    private static final Instant T0 = Instant.parse("2026-09-27T12:00:00Z");

    private final List<HistoryEventSummary> events = new ArrayList<>();

    private void add(CriticalEventType type, CriticalEvent payload) {
        events.add(new HistoryEventSummary("e" + events.size(), type, PI, DEF, null, payload.actorId(),
                T0.plusSeconds(events.size()), 0, payload));
    }

    private void started() {
        add(CriticalEventType.PROCESS_INSTANCE_STARTED, new ProcessInstanceStarted(PI, "757.491.186-06", DEF, "kyc", 1,
                Map.of(), T0, null, null, "site", null, null, null, "site-atoxfy-bank"));
    }

    private void node(String id, NodeExecutionStatus status, String interruptedBy) {
        add(CriticalEventType.FLOW_NODE_FINISHED, FlowNodeFinished.builder()
                .flowNodeDefinitionId(id).processInstanceId(PI).processDefinitionId(DEF)
                .nodeExecutionStatus(status).interruptedByNodeDefinitionId(interruptedBy).build());
    }

    private void completed(String taskId, String actor, String decisao) {
        add(CriticalEventType.EXTERNAL_TASK_COMPLETED, new ExternalTaskCompleted("t-" + taskId, DEF, PI, null, taskId,
                null, actor, T0, Map.of("decisaoRevisao", new ProcessVariable("decisaoRevisao", decisao))));
    }

    private void gateway(String id, String answer, String flowId) {
        add(CriticalEventType.GATEWAY_ANSWER_RESOLVED, new GatewayAnswerResolved(PI, DEF, null, "kyc", id,
                AnswerProviderType.VARIABLE, null, "decisaoRevisao", answer, flowId, T0));
    }

    private void finished() {
        add(CriticalEventType.PROCESS_INSTANCE_FINISHED, ProcessInstanceFinished.builder()
                .id(PI).processDefinitionId(DEF).processDefinitionKey("kyc").status(ProcessInstanceStatus.COMPLETED)
                .startedAt(T0).endedAt(T0.plusSeconds(60))
                .variables(Map.of("decisaoRevisao", new ProcessVariable("decisaoRevisao", "APROVAR"))).build());
    }

    private InstanceTrace assemble() {
        return InstanceTraceAssembler.assemble(events, null, Map.of(), null);
    }

    @Nested
    @DisplayName("Quando o analista aprova e a instância termina")
    class AnalistaAprova {

        @Test
        @DisplayName("Então o trace traz caminho, seta escolhida, quem aprovou e o que enviou")
        void caminhoCompleto() {
            started();
            node("INICIO", NodeExecutionStatus.SUCCESS, null);
            completed("ANALISE_ANALISTA", "ana.analista", "APROVAR");
            node("RESULTADO", NodeExecutionStatus.SUCCESS, null);
            gateway("RESULTADO", "APROVAR", "flow-aprovado");
            node("FIM", NodeExecutionStatus.SUCCESS, null);
            finished();

            InstanceTrace trace = assemble();

            assertEquals(ProcessInstanceStatus.COMPLETED, trace.instance().status());
            assertEquals("site-atoxfy-bank", trace.instance().startedBy());
            assertEquals(Map.of("INICIO", 1, "ANALISE_ANALISTA", 1, "RESULTADO", 1, "FIM", 1), trace.visitedNodes());
            assertEquals(List.of("flow-aprovado"), trace.takenFlowIds());
            assertEquals("APROVAR", trace.decisions().get(0).answer());
            assertEquals("decisaoRevisao", trace.decisions().get(0).provider());

            TraceHumanAction action = trace.humanActions().get(0);
            assertEquals("ANALISE_ANALISTA", action.nodeId());
            assertEquals("ana.analista", action.actorId());
            assertEquals("APROVAR", action.submittedVariables().get("decisaoRevisao").value());

            assertEquals("APROVAR", trace.finalVariables().get("decisaoRevisao").value());
            assertTrue(trace.interruptedNodes().isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando o SLA vence e o coordenador decide")
    class SlaEscala {

        @Test
        @DisplayName("Então o analista aparece interrompido, o timer percorrido e a ação humana é do coordenador")
        void analistaInterrompido() {
            started();
            node("ANALISE_ANALISTA", NodeExecutionStatus.INTERRUPTED, "SLA_ANALISTA");
            completed("ANALISE_COORDENADOR", "carlos.coordenador", "REPROVAR");

            InstanceTrace trace = assemble();

            assertEquals(Set.of("ANALISE_ANALISTA"), trace.interruptedNodes());
            assertEquals(1, trace.visitedNodes().get("SLA_ANALISTA"));
            assertEquals(1, trace.humanActions().size());
            assertEquals("carlos.coordenador", trace.humanActions().get(0).actorId());
        }
    }

    @Nested
    @DisplayName("Quando um nó roda duas vezes (laço) e uma tentativa falha")
    class LacoETentativa {

        @Test
        @DisplayName("Então conta duas passagens e ignora a tentativa com erro")
        void contaPassagens() {
            started();
            node("ANALISAR_DOCUMENTO", NodeExecutionStatus.ERROR, null);
            node("ANALISAR_DOCUMENTO", NodeExecutionStatus.SUCCESS, null);
            node("ANALISAR_DOCUMENTO", NodeExecutionStatus.SUCCESS, null);

            assertEquals(2, assemble().visitedNodes().get("ANALISAR_DOCUMENTO"));
        }
    }

    @Nested
    @DisplayName("Quando a instância ainda está ativa")
    class InstanciaAtiva {

        @Test
        @DisplayName("Então usa as variáveis atuais e mostra onde ela está")
        void usaEstadoAtual() {
            started();

            InstanceTrace trace = InstanceTraceAssembler.assemble(events, null, Map.of("AGUARDAR_ASSINATURA", 1, "ANTIGO", 0),
                    Map.of("chaveAssinatura", new ProcessVariable("chaveAssinatura", "CONTRATO_pi-1_ASSINADO")));

            assertEquals(ProcessInstanceStatus.ACTIVE, trace.instance().status());
            assertNull(trace.instance().endedAt());
            assertEquals(Map.of("AGUARDAR_ASSINATURA", 1), trace.activeNodes());
            assertEquals("CONTRATO_pi-1_ASSINADO", trace.finalVariables().get("chaveAssinatura").value());
        }
    }
}
