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

package io.kikwiflow.management.dtos;

import io.kikwiflow.model.execution.enumerated.IncidentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Corpo de {@code POST /incidents/search}. Todos os campos opcionais, combinados com {@code AND} —
 * mesmo desenho de {@link ProcessInstanceSearchRequest}. Mapeado para {@code IncidentQuery} por
 * {@code IncidentQueryMapper}.
 */
public record IncidentSearchRequest(
        String processDefinitionId,
        List<String> processDefinitionIds,
        String tenantId,
        List<String> tenantIds,
        String taskDefinitionId,
        String processInstanceId,
        List<IncidentStatus> statuses,
        String type,
        String message,
        Instant createdAfter,
        Instant createdBefore,
        String orderBy,
        Boolean ascending,
        Integer page,
        Integer size
) {

    public static final int MAX_SIZE = 100;

    public int getOrDefaultPage() {
        return page != null ? page : 0;
    }

    public int getOrDefaultSize() {
        int requestedSize = size != null ? size : 20;
        return requestedSize > 0 ? Math.min(requestedSize, MAX_SIZE) : 20;
    }

    public boolean isAscending() {
        return Boolean.TRUE.equals(ascending);
    }
}
