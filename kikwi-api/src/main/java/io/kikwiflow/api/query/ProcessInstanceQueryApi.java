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

package io.kikwiflow.api.query;

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.model.execution.ProcessInstance;

import java.util.List;

public interface ProcessInstanceQueryApi {
    CountResponse count(String processDefinitionId);
    ProcessInstance findProcessInstanceById(String processInstanceId);
    List<ProcessInstance> findAll(List<String> ids, String processDefinitionId, String tenantId);
}
