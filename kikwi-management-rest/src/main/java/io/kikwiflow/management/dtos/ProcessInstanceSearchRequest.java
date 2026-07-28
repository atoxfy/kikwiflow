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

import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProcessInstanceSearchRequest(
        String processDefinitionId,
        List<String> processDefinitionIds,
        List<String> processDefinitionKeys,
        String activeNodeId,
        String tenantId,
        List<String> tenantIds,
        List<ProcessInstanceStatus> statuses,
        String businessKey,
        List<String> businessKeys,
        Instant startedAfter,
        Instant startedBefore,
        Map<String, Object> variables,
        List<String> variablesExist,
        String orderBy,
        Boolean ascending,
        Integer page,
        Integer size
) {

    public int getOrDefaultPage() {
        return page != null ? page : 0;
    }

    public int getOrDefaultSize() {
        return size != null ? size : 20;
    }

    public boolean isAscending() {
        return Boolean.TRUE.equals(ascending);
    }
}
