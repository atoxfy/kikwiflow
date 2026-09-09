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

import io.kikwiflow.management.dtos.IncidentSearchRequest;
import io.kikwiflow.persistence.api.query.IncidentQuery;

import java.util.Set;

/**
 * Espelha {@link ProcessInstanceQueryMapper}: empurra cada campo de {@link IncidentSearchRequest}
 * para a {@link IncidentQuery} fluente e aplica a whitelist de {@code orderBy}.
 */
public final class IncidentQueryMapper {

    private IncidentQueryMapper() {}

    // Nomes de domínio de Incident. "id" é traduzido para o detalhe de armazenamento por cada
    // implementação de IncidentQuery (ex.: "_id" no Mongo), não aqui.
    private static final Set<String> ALLOWED_ORDER_BY_FIELDS = Set.of(
            "id", "createdAt", "status", "processDefinitionId", "taskDefinitionId"
    );

    public static IncidentQuery applyRequest(IncidentQuery query, IncidentSearchRequest request) {
        if (request == null) {
            return query;
        }

        query.processDefinitionId(request.processDefinitionId())
                .processDefinitionIdIn(request.processDefinitionIds())
                .tenantId(request.tenantId())
                .tenantIdIn(request.tenantIds())
                .taskDefinitionId(request.taskDefinitionId())
                .processInstanceId(request.processInstanceId())
                .statusIn(request.statuses())
                .type(request.type())
                .messageLike(request.message())
                .createdAfter(request.createdAfter())
                .createdBefore(request.createdBefore());

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
