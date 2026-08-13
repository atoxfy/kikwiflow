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
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.spring.rest.api.command.EventCorrelationOperationsRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Expõe {@code KikwiflowEngine.correlateMessage} via REST — o ponto de entrada para um webhook/sistema externo
 * entregar uma correlação (ex.: para um nó {@code EVENT_CATCHER} ou {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT})
 * sem precisar conhecer {@code processInstanceId}/{@code taskId}, só a própria chave de correlação. Ver
 * docs/engine/16-event-catcher-correlacao-de-eventos.md.
 */
@KikwiRestController
@ConditionalOnBean(KikwiflowEngine.class)
public class EventCorrelationCommandController implements EventCorrelationOperationsRestApi {

    private final KikwiflowEngine engine;

    public EventCorrelationCommandController(KikwiflowEngine engine) {
        this.engine = engine;
    }

    @Override
    public ProcessInstance correlateEvent(String correlationKey, CorrelateEventRequest correlateEventRequest, IdentityContext identityContext) {
        return engine.correlateMessage(correlationKey, correlateEventRequest.variables(), identityContext);
    }
}
