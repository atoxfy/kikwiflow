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

import io.kikwiflow.bpmn.mapper.end.EndEventMapper;
import io.kikwiflow.bpmn.mapper.start.StartEventMapper;
import io.kikwiflow.bpmn.mapper.task.ServiceTaskMapper;
import io.kikwiflow.bpmn.model.FlowNodeDefinition;
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.model.bpmn.elements.EndEventDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.StartEventDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.end.EndEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.start.StartEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceTaskEntity;

import java.util.Objects;

public final class FlowNodeMapper {

    private FlowNodeMapper() {
        // Utility class
    }


    public static FlowNodeDefinitionSnapshot toSnapshot(FlowNodeDefinitionEntity node) {

        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceTaskEntity) {
            return ServiceTaskMapper.toSnapshot((ServiceTaskEntity) node);

        } else if (node instanceof StartEventEntity) {
            return StartEventMapper.toSnapshot((StartEventEntity) node);

        } else if (node instanceof EndEventEntity) {
            return EndEventMapper.toSnapshot((EndEventEntity) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }

    public static FlowNodeDefinitionSnapshot toSnapshot(FlowNodeDefinition node) {

        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceTask) {
            return ServiceTaskMapper.toSnapshot((ServiceTask) node);

        } else if (node instanceof StartEvent) {
            return StartEventMapper.toSnapshot((StartEvent) node);

        } else if (node instanceof EndEvent ) {
            return EndEventMapper.toSnapshot((EndEvent) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }

    public static FlowNodeDefinitionEntity toEntity(FlowNodeDefinitionSnapshot node) {
        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceTaskDefinitionSnapshot) {
            return ServiceTaskMapper.toEntity((ServiceTaskDefinitionSnapshot) node);

        } else if (node instanceof StartEventDefinitionSnapshot) {
            return StartEventMapper.toEntity((StartEventDefinitionSnapshot) node);

        } else if (node instanceof EndEventDefinitionSnapshot) {
            return EndEventMapper.toEntity((EndEventDefinitionSnapshot) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }
}