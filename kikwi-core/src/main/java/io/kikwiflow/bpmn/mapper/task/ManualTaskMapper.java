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

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.mapper.boundary.BoundaryEventMapper;
import io.kikwiflow.bpmn.model.task.ManualTask;
import io.kikwiflow.model.definition.process.elements.ManualTaskDefinition;

import java.util.Objects;

public class ManualTaskMapper {

    private ManualTaskMapper() {
        // Utility class
    }

    public static ManualTaskDefinition  toSnapshot(final ManualTask manualTask) {
        if (Objects.isNull(manualTask)) {
            return null;
        }
        return ManualTaskDefinition.builder()
                .id(manualTask.getId())
                .name(manualTask.getName())
                .description(manualTask.getDescription())
                .commitAfter(manualTask.getCommitAfter())
                .commitBefore(manualTask.getCommitBefore())
                .outgoing(SequenceFlowMapper.toSnapshot(manualTask.getOutgoing()))
                .boundaryEvents(BoundaryEventMapper.toSnapshot(manualTask.getBoundaryEvents()))
                .extensionProperties(manualTask.getExtensionProperties())
                .build();
    }
}
