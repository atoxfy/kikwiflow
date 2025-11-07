/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;

import java.util.List;
import java.util.Optional;

public interface QueryRepository {

    List<ProcessDefinition> findAProcessDefinitionsByParams(String key);

    Optional<ProcessInstance> findProcessInstanceById(String processInstanceId);

    List<ProcessInstance> findProcessInstancesByIdIn(List<String> ids);

    List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId);

    Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey);

    Optional<ExternalTask> findExternalTaskById(String externalTaskId);

    Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId);

    Optional<ExecutableTask> findExecutableTaskById(String executableTaskId);

    Optional<ExecutableTask> findAndGetFirstPendingExecutableTask(String id);

    List<ProcessInstance> findProcessInstanceByProcessDefinitionId(String processDefinitionId, String tenantId);

    List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, String tenantId);

    List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId);

    List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, List<String> tenantIds);

    List<ExternalTask> findExternalTasksByAssignee(String assignee, String tenantId);

    ExternalTaskQuery createExternalTaskQuery();
}
