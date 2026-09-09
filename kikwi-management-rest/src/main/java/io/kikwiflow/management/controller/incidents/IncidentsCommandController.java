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

package io.kikwiflow.management.controller.incidents;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.dtos.IncidentRetryRequest;
import io.kikwiflow.management.dtos.IncidentRetryResponse;
import io.kikwiflow.management.dtos.IncidentRetryResult;
import io.kikwiflow.management.mapper.IncidentQueryMapper;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.List;

@KikwiRestController
@ConditionalOnBean({KikwiflowEngine.class, QueryRepository.class})
@RequestMapping("/incidents")
public class IncidentsCommandController {

    /** Diferente de /search (só leitura), este endpoint executa um retry por item. */
    private static final int MAX_INCIDENTS_PER_CALL = 100;

    private final KikwiflowEngine engine;
    private final QueryRepository queryRepository;

    public IncidentsCommandController(KikwiflowEngine engine, QueryRepository queryRepository) {
        this.engine = engine;
        this.queryRepository = queryRepository;
    }

    @PutMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable("id") String id, IdentityContext identityContext) {
        engine.retryIncident(id, identityContext);
    }

    /**
     * Retry em lote. {@code incidentIds} ou {@code filter} (um só). Sempre {@code 200 OK} —
     * falhas parciais vêm no corpo. Ver especificacao-incidentes-busca-e-retry-em-lote.md §5.
     */
    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.OK)
    public IncidentRetryResponse retryBatch(@RequestBody IncidentRetryRequest request, IdentityContext identityContext) {
        List<String> ids = resolveIds(request);

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

    private List<String> resolveIds(IncidentRetryRequest request) {
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
            if (ids.size() > MAX_INCIDENTS_PER_CALL) {
                throw new IllegalArgumentException(
                        ids.size() + " incidentes informados, máximo por chamada é " + MAX_INCIDENTS_PER_CALL
                                + ". Divida em lotes menores.");
            }
            return ids;
        }

        // filter: roda a query só para resolver os ids (paginação do filtro ignorada; um page grande
        // o bastante para detectar estouro do teto sem truncar silenciosamente).
        PageResult<Incident> matched = IncidentQueryMapper
                .applyRequest(queryRepository.createIncidentQuery(), request.filter())
                .page(0)
                .size(MAX_INCIDENTS_PER_CALL + 1)
                .list();

        if (matched.totalElements() > MAX_INCIDENTS_PER_CALL) {
            throw new IllegalArgumentException(
                    matched.totalElements() + " incidentes encontrados, máximo por chamada é " + MAX_INCIDENTS_PER_CALL
                            + ". Refine o filtro (ex.: adicione `taskDefinitionId` ou um intervalo de datas).");
        }

        return matched.content().stream().map(Incident::id).toList();
    }
}
