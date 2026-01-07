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
package io.kikwiflow.bpmn.mapper;

import io.kikwiflow.bpmn.model.ProcessDefinitionGraph;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ProcessDefinitionMapper {

    private ProcessDefinitionMapper() {
        // Utility class
    }

    public static ProcessDefinition toSnapshot(final ProcessDefinitionGraph processDefinitionGraphDeploy) {
        if (Objects.isNull(processDefinitionGraphDeploy)) {
            return null;
        }

        final Map<String, FlowNodeDefinition> flowNodeSnapshots = processDefinitionGraphDeploy.getFlowNodes()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> FlowNodeMapper.toRecord(entry.getValue())));


        return new ProcessDefinition(UUID.randomUUID().toString(), null, null, processDefinitionGraphDeploy.getKey(), processDefinitionGraphDeploy.getName(), processDefinitionGraphDeploy.getDescription(), flowNodeSnapshots, FlowNodeMapper.toRecord(processDefinitionGraphDeploy.getDefaultStartPoint()), processDefinitionGraphDeploy.getChecksum(), null);
    }
}
