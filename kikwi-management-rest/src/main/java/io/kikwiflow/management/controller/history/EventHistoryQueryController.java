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

import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.mapper.HistoryEventSummaryMapper;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Timeline de eventos críticos de uma instância de processo — a base para telas de troubleshooting (ex.:
 * Kikwiflow Monitor). Registrado condicionalmente a {@code kikwiflow.history.enabled} (default {@code true},
 * ver {@code KikwiRestAutoConfiguration}) — nem todo player quer expor esse histórico, mesmo com
 * {@code kikwiflow.outbox.events-enabled=true}.
 * <p>
 * Sem enforcement de tenant aqui de propósito: este endpoint é para uso backoffice/suporte (o "Cockpit" do
 * Kikwiflow), não uma API de uso geral de aplicação cliente — quem acessa já tem permissão independente de
 * tenant. Ver {@code docs/engine/09-eventos-e-observabilidade.md}.
 */
@KikwiRestController
@RequestMapping("/process-instances")
public class EventHistoryQueryController {

    private final QueryRepository queryRepository;
    private final VariableSecurityPolicyManager variableSecurityPolicyManager;

    public EventHistoryQueryController(QueryRepository queryRepository, VariableSecurityPolicyManager variableSecurityPolicyManager) {
        this.queryRepository = queryRepository;
        this.variableSecurityPolicyManager = variableSecurityPolicyManager;
    }

    @GetMapping("/{id}/events")
    @ResponseStatus(HttpStatus.OK)
    public List<HistoryEventSummary> getEventHistory(@PathVariable("id") String id, IdentityContext identityContext) {
        queryRepository.findProcessInstanceById(id)
                .orElseThrow(() -> new NotFoundException("Process instance not found with id: " + id));

        return queryRepository.findEventHistoryByProcessInstanceId(id).stream()
                .map(entity -> HistoryEventSummaryMapper.from(entity, variableSecurityPolicyManager, identityContext))
                .toList();
    }
}
