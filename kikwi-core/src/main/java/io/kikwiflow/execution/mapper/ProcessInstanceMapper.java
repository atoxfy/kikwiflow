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
package io.kikwiflow.execution.mapper;

import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.execution.ProcessInstanceExecution;

import java.util.HashMap;
import java.util.Map;

public final class ProcessInstanceMapper {

    private ProcessInstanceMapper() {
        // Utility class
    }

    public static ProcessInstanceFinished maoToFinishedEvent(final ProcessInstance processInstance) {
        ProcessInstanceFinished processInstanceEntity = new ProcessInstanceFinished();
        processInstanceEntity.setId(processInstance.id());
        processInstanceEntity.setBusinessKey(processInstance.businessKey());
        processInstanceEntity.setStatus(processInstance.status());
        processInstanceEntity.setProcessDefinitionId(processInstance.processDefinitionId());
        processInstanceEntity.setVariables(new HashMap<>(processInstance.variables()));
        processInstanceEntity.setStartedAt(processInstance.startedAt());
        processInstanceEntity.setEndedAt(processInstance.endedAt());
        return processInstanceEntity;
    }

    public static ProcessInstance mapToRecord(final ProcessInstanceExecution instance) {
        return new ProcessInstance(
            instance.getId(),
            instance.getBusinessKey(),
            instance.getBusinessValue(),
            instance.getTenantId(),
            instance.getStatus(),
            instance.getProcessDefinitionId(),
            Map.copyOf(instance.getVariables()),
            instance.getStartedAt(),
            instance.getEndedAt()
        );
    }


    public static ProcessInstanceExecution mapToInstanceExecution(ProcessInstance processInstance) {
        ProcessInstanceExecution processInstanceEntity = new ProcessInstanceExecution();
        processInstanceEntity.setId(processInstance.id());
        processInstanceEntity.setBusinessKey(processInstance.businessKey());
        processInstanceEntity.setStatus(processInstance.status());
        processInstanceEntity.setProcessDefinitionId(processInstance.processDefinitionId());
        processInstanceEntity.setVariables(new HashMap<>(processInstance.variables()));
        processInstanceEntity.setStartedAt(processInstance.startedAt());
        processInstanceEntity.setEndedAt(processInstance.endedAt());
        return processInstanceEntity;
    }
}