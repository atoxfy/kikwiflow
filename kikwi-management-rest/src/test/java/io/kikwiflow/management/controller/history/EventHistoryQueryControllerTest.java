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
package io.kikwiflow.management.controller.history;

import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Dado o endpoint GET /process-instances/{id}/events")
class EventHistoryQueryControllerTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    private final QueryRepository queryRepository = mock(QueryRepository.class);
    private final EventHistoryQueryController controller =
            new EventHistoryQueryController(queryRepository, mock(VariableSecurityPolicyManager.class));

    @Nested
    @DisplayName("Quando a instância já foi concluída e saiu de process_instances")
    class InstanciaConcluida {

        @Test
        @DisplayName("Então devolve o histórico do outbox, com quem concluiu cada tarefa")
        void devolveHistorico() {
            ExternalTaskCompleted completed = new ExternalTaskCompleted("task-1", "def-1", "pi-1", null,
                    "ANALISE_ANALISTA", null, "ana.analista", Instant.parse("2026-09-27T12:00:00Z"), null);
            when(queryRepository.findEventHistoryByProcessInstanceId("pi-1"))
                    .thenReturn(List.of(new OutboxEventEntity(CriticalEventType.EXTERNAL_TASK_COMPLETED, completed)));
            when(queryRepository.findProcessInstanceById("pi-1")).thenReturn(Optional.empty());

            List<HistoryEventSummary> history = controller.getEventHistory("pi-1", IDENTITY);

            assertEquals(1, history.size());
            assertEquals(CriticalEventType.EXTERNAL_TASK_COMPLETED, history.get(0).eventType());
            assertEquals("ana.analista", history.get(0).actorId());
        }
    }

    @Nested
    @DisplayName("Quando a instância está ativa mas ainda não tem eventos (outbox desligado)")
    class InstanciaAtivaSemEventos {

        @Test
        @DisplayName("Então devolve lista vazia, não 404")
        void devolveListaVazia() {
            when(queryRepository.findEventHistoryByProcessInstanceId("pi-2")).thenReturn(List.of());
            when(queryRepository.findProcessInstanceById("pi-2")).thenReturn(Optional.of(ProcessInstance.builder()
                    .id("pi-2").processDefinitionId("def-1").status(ProcessInstanceStatus.ACTIVE).build()));

            assertTrue(controller.getEventHistory("pi-2", IDENTITY).isEmpty());
        }
    }

    @Nested
    @DisplayName("Quando o id não corresponde a instância nem a evento algum")
    class IdDesconhecido {

        @Test
        @DisplayName("Então responde 404")
        void respondeNotFound() {
            when(queryRepository.findEventHistoryByProcessInstanceId("nao-existe")).thenReturn(List.of());
            when(queryRepository.findProcessInstanceById("nao-existe")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> controller.getEventHistory("nao-existe", IDENTITY));
        }
    }
}
