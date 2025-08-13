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
package io.kikwiflow.bpmn.mapper;

import io.kikwiflow.bpmn.model.FlowNodeDefinition;
import io.kikwiflow.bpmn.model.ProcessDefinition;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.ProcessDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ProcessDefinitionMapper {

    private ProcessDefinitionMapper() {
        // Utility class
    }


    public static ProcessDefinitionSnapshot toSnapshot(final ProcessDefinitionEntity processDefinitionDeploy) {
        if (Objects.isNull(processDefinitionDeploy)) {
            return null;
        }

        final Map<String, FlowNodeDefinitionSnapshot> flowNodeSnapshots = processDefinitionDeploy.getFlowNodes()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> FlowNodeMapper.toSnapshot(entry.getValue())));


        return new ProcessDefinitionSnapshot(null, null, processDefinitionDeploy.getKey(), processDefinitionDeploy.getName(), flowNodeSnapshots, FlowNodeMapper.toSnapshot(processDefinitionDeploy.getDefaultStartPoint()));
    }



    public static ProcessDefinitionSnapshot toSnapshot(final ProcessDefinition processDefinitionDeploy) {
        if (Objects.isNull(processDefinitionDeploy)) {
            return null;
        }

        final Map<String, FlowNodeDefinitionSnapshot> flowNodeSnapshots = processDefinitionDeploy.getFlowNodes()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> FlowNodeMapper.toSnapshot(entry.getValue())));


        return new ProcessDefinitionSnapshot(null, null, processDefinitionDeploy.getKey(), processDefinitionDeploy.getName(), flowNodeSnapshots, FlowNodeMapper.toSnapshot(processDefinitionDeploy.getDefaultStartPoint()));
    }

    public static ProcessDefinitionEntity mapToEntity(ProcessDefinitionSnapshot processDefinitionDeploy) {
        if (Objects.isNull(processDefinitionDeploy)) {
            return null;
        }

        final Map<String, FlowNodeDefinitionEntity> flowNodeSnapshots = processDefinitionDeploy.flowNodes()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> FlowNodeMapper.toEntity(entry.getValue())));

        return ProcessDefinitionEntity.builder()
                .id(processDefinitionDeploy.id())
                .key(processDefinitionDeploy.key())
                .defaultStartPoint(FlowNodeMapper.toEntity(processDefinitionDeploy.defaultStartPoint()))
                .name(processDefinitionDeploy.name())
                .flowNodes(flowNodeSnapshots)
                .build();

    }
}
