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

import io.kikwiflow.management.dtos.ProcessInstanceSearchRequest;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;

import java.util.Map;
import java.util.Set;

public final class ProcessInstanceQueryMapper {

    private ProcessInstanceQueryMapper() {}

    // Campos de ProcessInstanceSummary (o que listSummary() realmente projeta e devolve) que fazem sentido como
    // ordenação — nomes de domínio, não de armazenamento. Sem esta whitelist, orderBy ia direto para
    // Sorts.ascending/descending com qualquer valor vindo do cliente. Tradução para detalhes de storage (ex.:
    // "id" -> "_id" no Mongo) é responsabilidade de cada implementação de ProcessInstanceQuery, não deste
    // mapper — o backend in-memory usado pelos testes do motor espera o nome de campo literal "id".
    private static final Set<String> ALLOWED_ORDER_BY_FIELDS = Set.of(
            "id", "businessKey", "status", "processDefinitionId", "startedAt", "endedAt"
    );

    public static ProcessInstanceQuery applyRequest(ProcessInstanceQuery query, ProcessInstanceSearchRequest request) {
        if (request == null) {
            return query;
        }

        query.processDefinitionId(request.processDefinitionId())
                .processDefinitionIdIn(request.processDefinitionIds())
                .processDefinitionKeyIn(request.processDefinitionKeys())
                .activeNodeId(request.activeNodeId())
                .parentInstanceId(request.parentInstanceId())
                .tenantId(request.tenantId())
                .tenantIdIn(request.tenantIds())
                .statusIn(request.statuses())
                .businessKey(request.businessKey())
                .businessKeyIn(request.businessKeys())
                .startedAfter(request.startedAfter())
                .startedBefore(request.startedBefore());

        if (request.variables() != null && !request.variables().isEmpty()) {
            for (Map.Entry<String, Object> entry : request.variables().entrySet()) {
                query.variableEquals(entry.getKey(), entry.getValue());
            }
        }

        if (request.variablesExist() != null && !request.variablesExist().isEmpty()) {
            for (String variableName : request.variablesExist()) {
                query.variableExists(variableName);
            }
        }

        query.orderBy(resolveOrderByField(request.orderBy()), request.isAscending())
                .page(request.getOrDefaultPage())
                .size(request.getOrDefaultSize());

        return query;
    }

    private static String resolveOrderByField(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return null;
        }
        if (!ALLOWED_ORDER_BY_FIELDS.contains(orderBy)) {
            throw new IllegalArgumentException(
                    "Campo de ordenação inválido: '" + orderBy + "'. Valores aceitos: " + ALLOWED_ORDER_BY_FIELDS);
        }
        return orderBy;
    }
}