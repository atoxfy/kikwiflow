/*
 * Copyright Atoxfy and/or licensed to Atoxfy
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

import io.kikwiflow.event.model.ProcessInstanceFinishedEvent;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.model.execution.ProcessInstance;

public final class ProcessInstanceMapper {

    private ProcessInstanceMapper() {
        // Utility class
    }

    public static ProcessInstanceFinishedEvent toFinishedEvent(final ProcessInstance instance) {
        return ProcessInstanceFinishedEvent.builder()
            .id(instance.getId())
            .businessKey(instance.getBusinessKey())
            .processDefinitionId(instance.getProcessDefinitionId())
            .status(instance.getStatus())
            .startedAt(instance.getStartedAt())
            .endedAt(instance.getEndedAt())
            .variables(instance.getVariables())
            .build();
    }

    public static ProcessInstanceSnapshot toSnapshot(final ProcessInstance instance) {
        return new ProcessInstanceSnapshot(
            instance.getId(),
            instance.getBusinessKey(),
            instance.getStatus(),
            instance.getProcessDefinitionId(),
            instance.getVariables(),
            instance.getStartedAt(),
            instance.getEndedAt()
        );
    }

    public static ProcessInstance toProcessInstance(ProcessInstanceSnapshot processInstanceSnapshot) {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(processInstanceSnapshot.id());
        processInstance.setBusinessKey(processInstanceSnapshot.businessKey());
        processInstance.setStatus(processInstanceSnapshot.status());
        processInstance.setProcessDefinitionId(processInstanceSnapshot.processDefinitionId());
        processInstance.setVariables(processInstanceSnapshot.variables());
        processInstance.setStartedAt(processInstanceSnapshot.startedAt());
        processInstance.setEndedAt(processInstanceSnapshot.endedAt());
        return processInstance;
    }
}