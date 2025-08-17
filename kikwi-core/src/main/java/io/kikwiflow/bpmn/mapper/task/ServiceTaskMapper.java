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

package io.kikwiflow.bpmn.mapper.task;

import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;

import java.util.Objects;
import java.util.stream.Collectors;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;

public final class ServiceTaskMapper {

    private ServiceTaskMapper() {
        // Utility class
    }

    public static ServiceTaskDefinition toSnapshot(final ServiceTask serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return ServiceTaskDefinition.builder()
                .id(serviceTask.getId())
                .name(serviceTask.getName())
                .description(serviceTask.getDescription())
                .delegateExpression(serviceTask.getDelegateExpression())
                .commitAfter(serviceTask.getCommitAfter())
                .commitBefore(serviceTask.getCommitBefore())
                .outgoing(serviceTask.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }
}

