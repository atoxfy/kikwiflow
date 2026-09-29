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
import io.kikwiflow.management.history.InstanceTrace;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.history.HistoricInstanceCriteria;
import io.kikwiflow.persistence.api.history.HistoricInstancePage;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Dado o explorador de histórico (/history)")
class HistoryExplorerControllerTest {

    private static final IdentityContext IDENTITY = new IdentityContext("auditor", null);

    private final QueryRepository queryRepository = mock(QueryRepository.class);
    private final VariableSecurityPolicyManager securityPolicyManager = mock(VariableSecurityPolicyManager.class);
    private final HistoryExplorerController controller = new HistoryExplorerController(queryRepository, securityPolicyManager);

    @Nested
    @DisplayName("Quando busca instâncias")
    class Busca {

        @Test
        @DisplayName("Então repassa os filtros, tratando texto em branco como ausente")
        void repassaFiltros() {
            HistoricInstancePage page = new HistoricInstancePage(List.of(), 0, 1, 50);
            when(queryRepository.searchHistoricInstances(any())).thenReturn(page);
            Instant from = Instant.parse("2026-09-01T00:00:00Z");

            HistoricInstancePage result = controller.searchInstances("kyc", " ", ProcessInstanceStatus.COMPLETED, from, null,
                    "ana.analista", 1, 50);

            ArgumentCaptor<HistoricInstanceCriteria> criteria = ArgumentCaptor.forClass(HistoricInstanceCriteria.class);
            verify(queryRepository).searchHistoricInstances(criteria.capture());
            assertEquals(new HistoricInstanceCriteria("kyc", null, ProcessInstanceStatus.COMPLETED, from, null,
                    "ana.analista", 1, 50), criteria.getValue());
            assertEquals(page, result);
        }

        @Test
        @DisplayName("Então recusa página maior que 100 ou negativa (400)")
        void recusaPaginaInvalida() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.searchInstances(null, null, null, null, null, null, 0, 101));
            assertThrows(IllegalArgumentException.class,
                    () -> controller.searchInstances(null, null, null, null, null, null, -1, 20));
        }
    }

    @Nested
    @DisplayName("Quando pede o trace")
    class Trace {

        @Test
        @DisplayName("Então monta o trace de uma instância já finalizada, com as variáveis enviadas mascaradas")
        void traceDeFinalizada() {
            ExternalTaskCompleted completed = new ExternalTaskCompleted("t-1", "def-1", "pi-1", null, "ANALISE_ANALISTA",
                    null, "ana.analista", Instant.now(), Map.of("cpf", new ProcessVariable("cpf", "757.491.186-06")));
            when(queryRepository.findEventHistoryByProcessInstanceId("pi-1"))
                    .thenReturn(List.of(new OutboxEventEntity(CriticalEventType.EXTERNAL_TASK_COMPLETED, completed)));
            when(queryRepository.findProcessInstanceById("pi-1")).thenReturn(Optional.empty());
            when(queryRepository.findProcessDefinitionById("def-1")).thenReturn(Optional.empty());
            when(securityPolicyManager.applyReadPoliciesAndMasking(anyString(), any(), anyMap()))
                    .thenReturn(Map.of("cpf", new ProcessVariable("cpf", "***.***.***-06")));

            InstanceTrace trace = controller.getTrace("pi-1", IDENTITY);

            assertNull(trace.instance(), "sem PROCESS_INSTANCE_STARTED no outbox, não há cabeçalho");
            assertEquals("ana.analista", trace.humanActions().get(0).actorId());
            assertEquals("***.***.***-06", trace.humanActions().get(0).submittedVariables().get("cpf").value());
            assertEquals(Map.of(), trace.activeNodes());
        }

        @Test
        @DisplayName("Então responde 404 para um id sem instância nem eventos")
        void naoEncontrada() {
            when(queryRepository.findEventHistoryByProcessInstanceId("x")).thenReturn(List.of());
            when(queryRepository.findProcessInstanceById("x")).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> controller.getTrace("x", IDENTITY));
        }
    }
}
