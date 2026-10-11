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
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.management.dtos.IncidentRetryRequest;
import io.kikwiflow.management.dtos.IncidentRetryResponse;
import io.kikwiflow.management.dtos.IncidentRetryResult;
import io.kikwiflow.management.mapper.IncidentQueryMapper;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Retry de incidentes, individual e em lote. Compartilhado entre o REST ({@code IncidentsCommandController}) e o
 * servidor MCP — os dois canais aplicam as mesmas regras (ids XOR filtro, teto por chamada, falha parcial no corpo).
 * Ver especificacao-incidentes-busca-e-retry-em-lote.md §5.
 */
public class IncidentRetryService {

    /** Teto do REST. Outros canais (MCP) podem passar um teto menor em {@link #resolveIds(IncidentRetryRequest, int)}. */
    public static final int MAX_INCIDENTS_PER_CALL = 100;

    private final KikwiflowEngine engine;
    private final QueryRepository queryRepository;

    public IncidentRetryService(KikwiflowEngine engine, QueryRepository queryRepository) {
        this.engine = engine;
        this.queryRepository = queryRepository;
    }

    public void retry(String incidentId, IdentityContext identityContext) {
        engine.retryIncident(incidentId, identityContext);
    }

    public IncidentRetryResponse retryBatch(IncidentRetryRequest request, IdentityContext identityContext) {
        return retryAll(resolveIds(request, MAX_INCIDENTS_PER_CALL), identityContext);
    }

    /** Reprocessa exatamente os ids informados (já resolvidos/validados por quem chama). Falhas vêm por item. */
    public IncidentRetryResponse retryAll(List<String> ids, IdentityContext identityContext) {
        List<IncidentRetryResult> results = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                engine.retryIncident(id, identityContext);
                results.add(IncidentRetryResult.retried(id));
            } catch (TaskNotFoundException ex) {
                results.add(IncidentRetryResult.failed(id, "NOT_FOUND", ex.getMessage()));
            } catch (IllegalStateException ex) {
                results.add(IncidentRetryResult.failed(id, "CONFLICT", ex.getMessage()));
            } catch (RuntimeException ex) {
                results.add(IncidentRetryResult.failed(id, "INTERNAL_ERROR", ex.getMessage()));
            }
        }
        return IncidentRetryResponse.from(results);
    }

    /**
     * Resolve os ids alvo de um pedido de retry em lote: {@code incidentIds} ou {@code filter} (um só), no máximo
     * {@code maxPerCall}. Lança {@link IllegalArgumentException} (→ 400) em pedido inválido ou acima do teto.
     */
    public List<String> resolveIds(IncidentRetryRequest request, int maxPerCall) {
        boolean hasIds = request != null && request.incidentIds() != null && !request.incidentIds().isEmpty();
        boolean hasFilter = request != null && request.filter() != null;

        if (!hasIds && !hasFilter) {
            throw new IllegalArgumentException("Informe `incidentIds` ou `filter`.");
        }
        if (hasIds && hasFilter) {
            throw new IllegalArgumentException("Informe apenas um dos dois, `incidentIds` ou `filter`, não os dois.");
        }

        if (hasIds) {
            List<String> ids = request.incidentIds();
            if (ids.size() > maxPerCall) {
                throw new IllegalArgumentException(
                        ids.size() + " incidentes informados, máximo por chamada é " + maxPerCall
                                + ". Divida em lotes menores.");
            }
            return ids;
        }

        // filter: roda a query só para resolver os ids (paginação do filtro ignorada; um page grande
        // o bastante para detectar estouro do teto sem truncar silenciosamente).
        PageResult<Incident> matched = IncidentQueryMapper
                .applyRequest(queryRepository.createIncidentQuery(), request.filter())
                .page(0)
                .size(maxPerCall + 1)
                .list();

        if (matched.totalElements() > maxPerCall) {
            throw new IllegalArgumentException(
                    matched.totalElements() + " incidentes encontrados, máximo por chamada é " + maxPerCall
                            + ". Refine o filtro (ex.: adicione `taskDefinitionId` ou um intervalo de datas).");
        }

        return matched.content().stream().map(Incident::id).toList();
    }
}
