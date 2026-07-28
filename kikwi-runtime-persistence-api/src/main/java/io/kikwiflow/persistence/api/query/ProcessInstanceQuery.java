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

import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.shared.PageResult;

import java.time.Instant;
import java.util.List;

public interface ProcessInstanceQuery {
    ProcessInstanceQuery processDefinitionId(String processDefinitionId);
    ProcessInstanceQuery processDefinitionIdIn(List<String> processDefinitionIds);
    ProcessInstanceQuery processDefinitionKeyIn(List<String> processDefinitionKeys);

    ProcessInstanceQuery activeNodeId(String activeNodeId);

    ProcessInstanceQuery tenantId(String tenantId);
    ProcessInstanceQuery tenantIdIn(List<String> tenantIds);

    ProcessInstanceQuery statusIn(List<ProcessInstanceStatus> statuses);

    ProcessInstanceQuery businessKey(String businessKey);
    ProcessInstanceQuery businessKeyIn(List<String> businessKeys);

    ProcessInstanceQuery startedAfter(Instant startedAfter);
    ProcessInstanceQuery startedBefore(Instant startedBefore);

    ProcessInstanceQuery variableEquals(String key, Object value);
    ProcessInstanceQuery variableExists(String key);

    ProcessInstanceQuery orderBy(String field, boolean ascending);
    ProcessInstanceQuery page(int page);
    ProcessInstanceQuery size(int size);

    PageResult<ProcessInstanceSummary> listSummary();
}