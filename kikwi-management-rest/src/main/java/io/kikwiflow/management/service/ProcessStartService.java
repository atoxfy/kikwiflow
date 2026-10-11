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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.api.dto.ProcessInstanceStartRequest;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.security.IdentityContext;

/**
 * Início de instâncias para os canais de gestão (REST e MCP). Diferente de chamar o {@code ProcessStarter} direto,
 * grava quem iniciou ({@code byActor}) — sem isso o {@code PROCESS_INSTANCE_STARTED} do outbox fica sem ator e o
 * explorador de histórico não consegue responder "quem abriu esta instância".
 */
public class ProcessStartService {

    private final KikwiflowEngine engine;

    public ProcessStartService(KikwiflowEngine engine) {
        this.engine = engine;
    }

    /**
     * @param defaultOrigin usado quando o pedido não traz {@code origin} (ex.: {@code "mcp"}); pode ser null.
     */
    public ProcessInstance start(ProcessInstanceStartRequest request, IdentityContext identityContext, String defaultOrigin) {
        String origin = request.origin() != null && !request.origin().isBlank() ? request.origin() : defaultOrigin;
        return engine.startProcess()
                .byKey(request.processDefinitionKey())
                .from(origin)
                .onTenant(request.tenant())
                .withBusinessKey(request.businessKey())
                .withVariables(request.variables())
                .withBusinessValue(request.businessValue())
                .byActor(identityContext != null ? identityContext.actorId() : null)
                .execute();
    }
}
