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
import io.kikwiflow.management.history.InstanceTrace;
import io.kikwiflow.management.service.InstanceTraceService;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.history.HistoricInstanceCriteria;
import io.kikwiflow.persistence.api.history.HistoricInstancePage;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;

/**
 * Explorador de histórico (docs/engine/29): consumidor de leitura do outbox, grátis e in-process — lista
 * instâncias, inclusive as que já terminaram, e devolve o trace de uma delas. Registrado só com
 * {@code kikwiflow.history.explorer.enabled=true} (ver {@code KikwiRestAutoConfiguration}). O relay (push para
 * fora) é outra coisa, e não mora aqui.
 * <p>
 * Assim como {@link EventHistoryQueryController}, não há enforcement de tenant: é ferramenta de
 * backoffice/auditoria. Variáveis passam pela política de leitura antes de sair.
 */
@KikwiRestController
@RequestMapping("/history")
public class HistoryExplorerController {

    static final int MAX_PAGE_SIZE = 100;

    private final QueryRepository queryRepository;
    private final InstanceTraceService instanceTraceService;

    public HistoryExplorerController(QueryRepository queryRepository, VariableSecurityPolicyManager variableSecurityPolicyManager) {
        this.queryRepository = queryRepository;
        this.instanceTraceService = new InstanceTraceService(queryRepository, variableSecurityPolicyManager);
    }

    @GetMapping("/instances")
    @ResponseStatus(HttpStatus.OK)
    public HistoricInstancePage searchInstances(
            @RequestParam(required = false) String processDefinitionKey,
            @RequestParam(required = false) String businessKey,
            @RequestParam(required = false) ProcessInstanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedTo,
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page deve ser >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size deve estar entre 1 e " + MAX_PAGE_SIZE + ".");
        }
        return queryRepository.searchHistoricInstances(new HistoricInstanceCriteria(
                blankToNull(processDefinitionKey), blankToNull(businessKey), status, startedFrom, startedTo,
                blankToNull(actorId), page, size));
    }

    @GetMapping("/instances/{id}/trace")
    @ResponseStatus(HttpStatus.OK)
    public InstanceTrace getTrace(@PathVariable("id") String id, IdentityContext identityContext) {
        return instanceTraceService.getTrace(id, identityContext);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
