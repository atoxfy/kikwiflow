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
package io.kikwiflow.execution;

import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.history.HistoricInstanceCriteria;
import io.kikwiflow.persistence.api.history.HistoricInstancePage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explorador de histórico (docs/engine/29): a linha do tempo do outbox tem que sair em ordem causal, a conclusão
 * de uma tarefa humana tem que dizer o que foi enviado, e instâncias finalizadas continuam encontráveis.
 */
@DisplayName("Dado um processo com revisão humana e o outbox ligado")
class HistoryOrderingAndSearchTest {

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine()
                .withConfig(config -> config.setOutboxEventsEnabled(true))
                .build();
        definition = testEngine.deploy("/processes/history-review-flow.json");
    }

    private ProcessInstance start(String businessKey) {
        return testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey(businessKey)
                .byActor("site")
                .execute();
    }

    private void completeReview(ProcessInstance instance, String actor, String decisao) {
        ExternalTask review = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).get(0);
        testEngine.engine().completeExternalTask(review.id(),
                Map.of("decisao", new ProcessVariable("decisao", decisao)), new IdentityContext(actor, null));
    }

    private List<OutboxEventEntity> history(ProcessInstance instance) {
        return testEngine.repository().findEventHistoryByProcessInstanceId(instance.id());
    }

    private static List<String> timeline(List<OutboxEventEntity> events) {
        return events.stream().map(OutboxEventEntity::getEvent).toList();
    }

    @Nested
    @DisplayName("Quando a instância é iniciada")
    class QuandoIniciada {

        @Test
        @DisplayName("Então PROCESS_INSTANCE_STARTED vem antes do primeiro nó executado")
        void startedVemPrimeiro() {
            ProcessInstance instance = start("BK-1");

            List<String> timeline = timeline(history(instance));
            assertEquals(CriticalEventType.PROCESS_INSTANCE_STARTED.name(), timeline.get(0), "timeline: " + timeline);
        }
    }

    @Nested
    @DisplayName("Quando o analista conclui a revisão")
    class QuandoAnalistaConclui {

        @Test
        @DisplayName("Então a conclusão vem antes do gateway que ela disparou, com ator e variáveis enviadas")
        void conclusaoAntesDoGatewayComVariaveis() {
            ProcessInstance instance = start("BK-2");
            completeReview(instance, "ana.analista", "APROVAR");

            List<OutboxEventEntity> events = history(instance);
            List<String> timeline = timeline(events);
            int completed = timeline.indexOf(CriticalEventType.EXTERNAL_TASK_COMPLETED.name());
            int gateway = timeline.indexOf(CriticalEventType.GATEWAY_ANSWER_RESOLVED.name());
            int finished = timeline.indexOf(CriticalEventType.PROCESS_INSTANCE_FINISHED.name());
            assertTrue(completed >= 0 && completed < gateway && gateway < finished, "timeline: " + timeline);

            ExternalTaskCompleted payload = (ExternalTaskCompleted) events.get(completed).getPayload();
            assertEquals("ana.analista", payload.actorId());
            assertEquals("APROVAR", payload.submittedVariables().get("decisao").value());
        }

        @Test
        @DisplayName("Então a ordem do histórico é estável mesmo com commits no mesmo milissegundo")
        void ordemEstavel() {
            ProcessInstance instance = start("BK-3");
            completeReview(instance, "ana.analista", "APROVAR");

            List<OutboxEventEntity> events = history(instance);
            for (int i = 1; i < events.size(); i++) {
                OutboxEventEntity previous = events.get(i - 1);
                OutboxEventEntity current = events.get(i);
                assertTrue(previous.getSequence() < current.getSequence(),
                        "sequence tem que crescer ao longo do histórico, inclusive entre commits: " + timeline(events));
                assertTrue(!current.getTimestamp().isBefore(previous.getTimestamp()));
            }
        }
    }

    @Nested
    @DisplayName("Quando a tela reenvia a instância inteira junto com a decisão")
    class QuandoReenviaTudo {

        @Test
        @DisplayName("Então o evento de conclusão guarda só o que mudou ou foi acrescentado")
        void guardaSoOQueMudou() {
            ProcessInstance instance = testEngine.engine().startProcess()
                    .byKey(definition.key())
                    .withBusinessKey("BK-4")
                    .withVariables(Map.of("cpf", new ProcessVariable("cpf", "757.491.186-06"),
                            "limite", new ProcessVariable("limite", 5000)))
                    .execute();
            ExternalTask review = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id()).get(0);

            testEngine.engine().completeExternalTask(review.id(), Map.of(
                    "cpf", new ProcessVariable("cpf", "757.491.186-06"),
                    "limite", new ProcessVariable("limite", 5000L),
                    "decisao", new ProcessVariable("decisao", "APROVAR")), new IdentityContext("ana.analista", null));

            ExternalTaskCompleted completed = history(instance).stream()
                    .filter(e -> e.getPayload() instanceof ExternalTaskCompleted)
                    .map(e -> (ExternalTaskCompleted) e.getPayload())
                    .findFirst().orElseThrow();
            assertEquals(java.util.Set.of("decisao"), completed.submittedVariables().keySet());
        }
    }

    @Nested
    @DisplayName("Quando se busca no histórico")
    class QuandoBusca {

        private HistoricInstancePage search(ProcessInstanceStatus status, String actorId) {
            return testEngine.repository().searchHistoricInstances(
                    new HistoricInstanceCriteria(definition.key(), null, status, null, null, actorId, 0, 20));
        }

        @Test
        @DisplayName("Então instâncias finalizadas continuam aparecendo, com status, duração e quem iniciou")
        void finalizadasAparecem() {
            ProcessInstance done = start("BK-DONE");
            completeReview(done, "ana.analista", "REPROVAR");
            ProcessInstance waiting = start("BK-WAITING");

            HistoricInstancePage all = search(null, null);
            assertEquals(2, all.totalElements());
            assertEquals(waiting.id(), all.content().get(0).processInstanceId(), "mais recentes primeiro");

            HistoricInstancePage completed = search(ProcessInstanceStatus.COMPLETED, null);
            assertEquals(1, completed.totalElements());
            assertEquals(done.id(), completed.content().get(0).processInstanceId());
            assertEquals("site", completed.content().get(0).startedBy());
            assertNotNull(completed.content().get(0).durationMs());

            HistoricInstancePage active = search(ProcessInstanceStatus.ACTIVE, null);
            assertEquals(1, active.totalElements());
            assertEquals(waiting.id(), active.content().get(0).processInstanceId());
        }

        @Test
        @DisplayName("Então o filtro por ator encontra as instâncias em que ele agiu")
        void filtroPorAtor() {
            ProcessInstance byAna = start("BK-ANA");
            completeReview(byAna, "ana.analista", "APROVAR");
            ProcessInstance byCarlos = start("BK-CARLOS");
            completeReview(byCarlos, "carlos.coordenador", "APROVAR");

            HistoricInstancePage anaPage = search(null, "ana.analista");
            assertEquals(1, anaPage.totalElements());
            assertEquals(byAna.id(), anaPage.content().get(0).processInstanceId());
        }
    }
}
