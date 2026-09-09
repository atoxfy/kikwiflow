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

package io.kikwiflow.persistence.api.query;

import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.shared.PageResult;

import java.time.Instant;
import java.util.List;

/**
 * API fluente de consulta de incidentes — mesmo formato de {@link ProcessInstanceQuery} /
 * {@link ExternalTaskQuery}. Todos os filtros são opcionais e combinados com {@code AND}.
 *
 * <p>Não há {@code processDefinitionKeyIn}/{@code businessKey}: o record {@code Incident}
 * (kikwi-model) não carrega esses campos. O filtro por tenant é resolvido indiretamente pela
 * instância de processo dona do incidente (o incidente não persiste {@code tenantId}).
 */
public interface IncidentQuery {
    IncidentQuery processDefinitionId(String processDefinitionId);
    IncidentQuery processDefinitionIdIn(List<String> processDefinitionIds);

    IncidentQuery tenantId(String tenantId);
    IncidentQuery tenantIdIn(List<String> tenantIds);

    IncidentQuery taskDefinitionId(String taskDefinitionId);
    IncidentQuery processInstanceId(String processInstanceId);

    IncidentQuery statusIn(List<IncidentStatus> statuses);

    IncidentQuery type(String type);
    IncidentQuery messageLike(String messageSubstring);

    IncidentQuery createdAfter(Instant createdAfter);
    IncidentQuery createdBefore(Instant createdBefore);

    IncidentQuery orderBy(String field, boolean ascending);
    IncidentQuery page(int page);
    IncidentQuery size(int size);

    PageResult<Incident> list();
    long count();
}
