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

package io.kikwiflow.management.mapper;

import io.kikwiflow.model.event.CriticalEvent;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessVariableChanged;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;

import java.util.Map;

/**
 * Mapeia {@link OutboxEventEntity} para {@link HistoryEventSummary}, aplicando
 * {@link VariableSecurityPolicyManager#applyReadPoliciesAndMasking} sobre o valor de {@link ProcessVariableChanged}
 * antes de expor o payload — o único dos 11 tipos de evento crítico que carrega dado de variável de negócio
 * (os outros só carregam metadados de execução).
 */
public final class HistoryEventSummaryMapper {

    private HistoryEventSummaryMapper() {}

    public static HistoryEventSummary from(OutboxEventEntity entity, VariableSecurityPolicyManager securityPolicyManager,
                                           IdentityContext identityContext) {
        CriticalEvent payload = maskIfNeeded(entity.getPayload(), securityPolicyManager, identityContext);

        return new HistoryEventSummary(
                entity.getId(),
                CriticalEventType.valueOf(entity.getEvent()),
                payload.processInstanceId(),
                payload.processDefinitionId(),
                payload.tenantId(),
                payload.actorId(),
                entity.getTimestamp(),
                payload
        );
    }

    private static CriticalEvent maskIfNeeded(CriticalEvent payload, VariableSecurityPolicyManager securityPolicyManager,
                                              IdentityContext identityContext) {
        if (!(payload instanceof ProcessVariableChanged variableChanged)) {
            return payload;
        }

        // Uma remoção não carrega valor (já é null), então não há nada a mascarar — só o próprio "removed"
        // já é a informação completa do evento.
        if (variableChanged.removed()) {
            return payload;
        }

        ProcessVariable rawVariable = new ProcessVariable(variableChanged.name(), variableChanged.isTransient(), variableChanged.value());
        Map<String, ProcessVariable> masked = securityPolicyManager.applyReadPoliciesAndMasking(
                variableChanged.processDefinitionId(), identityContext, Map.of(variableChanged.name(), rawVariable));
        ProcessVariable maskedVariable = masked.get(variableChanged.name());

        // Se a policy retirar a variável do mapa (leitura negada), o valor vira null em vez de vazar o bruto —
        // "sem permissão" nunca deve degradar silenciosamente para "mostra mesmo assim".
        return new ProcessVariableChanged(
                variableChanged.processInstanceId(),
                variableChanged.processDefinitionId(),
                variableChanged.tenantId(),
                variableChanged.name(),
                variableChanged.isTransient(),
                maskedVariable != null ? maskedVariable.value() : null,
                variableChanged.actorId(),
                variableChanged.changedAt(),
                variableChanged.removed()
        );
    }
}
