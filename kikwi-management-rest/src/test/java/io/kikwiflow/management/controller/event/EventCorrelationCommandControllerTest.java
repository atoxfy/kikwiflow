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

package io.kikwiflow.management.controller.event;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.api.dto.CorrelateEventRequest;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.security.IdentityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Dado o endpoint POST /events/correlate/{correlationKey}")
class EventCorrelationCommandControllerTest {

    private static final IdentityContext IDENTITY = new IdentityContext("test-actor", "tenant-a");

    @Test
    @DisplayName("Delega para KikwiflowEngine.correlateMessage com a chave da URL e as variáveis do corpo")
    void delegatesToEngineCorrelateMessage() {
        KikwiflowEngine engine = mock(KikwiflowEngine.class);
        ProcessInstance completed = ProcessInstance.builder()
                .id("pi-1")
                .processDefinitionId("def-1")
                .businessKey("BK-1")
                .status(ProcessInstanceStatus.COMPLETED)
                .build();
        Map<String, ProcessVariable> variables = Map.of();
        when(engine.correlateMessage(eq("ORDER_1_PAID"), any(), eq(IDENTITY))).thenReturn(completed);

        EventCorrelationCommandController controller = new EventCorrelationCommandController(engine);

        ProcessInstance result = controller.correlateEvent("ORDER_1_PAID", new CorrelateEventRequest(variables), IDENTITY);

        assertEquals(completed, result);
        verify(engine).correlateMessage("ORDER_1_PAID", variables, IDENTITY);
    }

    @Test
    @DisplayName("Propaga TaskNotFoundException quando nenhum EVENT_CATCHER aguarda a chave — mapeado para 404 pelo KikwiflowExceptionHandler")
    void propagatesTaskNotFoundException() {
        KikwiflowEngine engine = mock(KikwiflowEngine.class);
        when(engine.correlateMessage(eq("chave-desconhecida"), any(), eq(IDENTITY)))
                .thenThrow(new TaskNotFoundException("Nenhum EVENT_CATCHER ativo aguardando a chave de correlação: chave-desconhecida"));

        EventCorrelationCommandController controller = new EventCorrelationCommandController(engine);

        assertThrows(TaskNotFoundException.class, () ->
                controller.correlateEvent("chave-desconhecida", new CorrelateEventRequest(Map.of()), IDENTITY));
    }
}
