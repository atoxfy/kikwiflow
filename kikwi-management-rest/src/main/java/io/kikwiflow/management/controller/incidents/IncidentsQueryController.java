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

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.dtos.IncidentSearchRequest;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.mapper.IncidentQueryMapper;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.query.IncidentQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@KikwiRestController
@ConditionalOnBean(QueryRepository.class)
@RequestMapping("/incidents")
public class IncidentsQueryController {

    private final QueryRepository queryRepository;

    public IncidentsQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @GetMapping("{id}")
    @ResponseStatus(HttpStatus.OK)
    public Incident getIncident(@PathVariable("id") String id) {
        return queryRepository.findIncidentById(id)
                .orElseThrow(() -> new NotFoundException("Incidente não encontrado com o id"));
    }

    /**
     * Contagem simples e combinável de incidentes — parâmetros escalares, sem paginação.
     * Mesmo estilo de {@code GET /external-tasks/count}.
     */
    @GetMapping("/count")
    @ResponseStatus(HttpStatus.OK)
    public CountResponse count(
            @RequestParam(name = "status", required = false) IncidentStatus status,
            @RequestParam(name = "process-definition-id", required = false) String processDefinitionId,
            @RequestParam(name = "tenant-id", required = false) String tenantId,
            @RequestParam(name = "task-definition-id", required = false) String taskDefinitionId
    ) {
        IncidentQuery query = queryRepository.createIncidentQuery()
                .processDefinitionId(processDefinitionId)
                .tenantId(tenantId)
                .taskDefinitionId(taskDefinitionId);
        if (status != null) {
            query.statusIn(List.of(status));
        }
        return new CountResponse(query.count());
    }

    /**
     * Busca paginada de incidentes com filtros combináveis ({@code AND}). Mesmo desenho de
     * {@code POST /process-instances/search}.
     */
    @PostMapping("/search")
    public ResponseEntity<PageResult<Incident>> search(@RequestBody IncidentSearchRequest searchRequest) {
        PageResult<Incident> result = IncidentQueryMapper
                .applyRequest(queryRepository.createIncidentQuery(), searchRequest)
                .list();
        return ResponseEntity.ok(result);
    }
}
