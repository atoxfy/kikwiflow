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

import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.history.InstanceTrace;
import io.kikwiflow.management.history.InstanceTraceAssembler;
import io.kikwiflow.management.mapper.HistoryEventSummaryMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monta o trace de uma instância (viva ou finalizada) a partir do outbox — base de
 * {@code GET /history/instances/{id}/trace} e da ferramenta MCP de linha do tempo. Variáveis sempre passam pelo
 * {@link VariableSecurityPolicyManager}. Ver docs/engine/29-explorador-de-historico.md.
 */
public class InstanceTraceService {

    private final QueryRepository queryRepository;
    private final VariableSecurityPolicyManager variableSecurityPolicyManager;

    public InstanceTraceService(QueryRepository queryRepository, VariableSecurityPolicyManager variableSecurityPolicyManager) {
        this.queryRepository = queryRepository;
        this.variableSecurityPolicyManager = variableSecurityPolicyManager;
    }

    /** @throws NotFoundException se não há eventos nem instância viva com esse id. */
    public InstanceTrace getTrace(String processInstanceId, IdentityContext identityContext) {
        List<OutboxEventEntity> rawEvents = queryRepository.findEventHistoryByProcessInstanceId(processInstanceId);
        Optional<ProcessInstance> live = queryRepository.findProcessInstanceById(processInstanceId);
        if (rawEvents.isEmpty() && live.isEmpty()) {
            throw new NotFoundException("Process instance not found with id: " + processInstanceId);
        }

        List<HistoryEventSummary> events = rawEvents.stream()
                .map(entity -> HistoryEventSummaryMapper.from(entity, variableSecurityPolicyManager, identityContext))
                .toList();

        String definitionId = live.map(ProcessInstance::processDefinitionId)
                .orElseGet(() -> rawEvents.get(0).getPayload().processDefinitionId());
        ProcessDefinition definition = definitionId != null
                ? queryRepository.findProcessDefinitionById(definitionId).orElse(null) : null;

        Map<String, ProcessVariable> liveVariables = live
                .map(instance -> instance.variables() == null ? Map.<String, ProcessVariable>of()
                        : variableSecurityPolicyManager.applyReadPoliciesAndMasking(definitionId, identityContext, instance.variables()))
                .orElse(null);

        return InstanceTraceAssembler.assemble(events, definition,
                live.map(ProcessInstance::activeNodes).orElse(Map.of()), liveVariables);
    }
}
