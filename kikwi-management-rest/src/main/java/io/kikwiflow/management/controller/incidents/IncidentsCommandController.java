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
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.dtos.IncidentRetryRequest;
import io.kikwiflow.management.dtos.IncidentRetryResponse;
import io.kikwiflow.management.service.IncidentRetryService;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@KikwiRestController
@ConditionalOnBean({KikwiflowEngine.class, QueryRepository.class})
@RequestMapping("/incidents")
public class IncidentsCommandController {

    private final IncidentRetryService incidentRetryService;

    public IncidentsCommandController(KikwiflowEngine engine, QueryRepository queryRepository) {
        this.incidentRetryService = new IncidentRetryService(engine, queryRepository);
    }

    @PutMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable("id") String id, IdentityContext identityContext) {
        incidentRetryService.retry(id, identityContext);
    }

    /**
     * Retry em lote. {@code incidentIds} ou {@code filter} (um só). Sempre {@code 200 OK} —
     * falhas parciais vêm no corpo. Ver especificacao-incidentes-busca-e-retry-em-lote.md §5.
     */
    @PostMapping("/retry")
    @ResponseStatus(HttpStatus.OK)
    public IncidentRetryResponse retryBatch(@RequestBody IncidentRetryRequest request, IdentityContext identityContext) {
        return incidentRetryService.retryBatch(request, identityContext);
    }
}
