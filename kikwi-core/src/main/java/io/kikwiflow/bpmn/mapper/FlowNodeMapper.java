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
import io.kikwiflow.bpmn.mapper.task.HumanTaskMapper;
import io.kikwiflow.bpmn.mapper.task.ServiceTaskMapper;
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.bpmn.model.task.HumanTask;
import io.kikwiflow.bpmn.model.task.Service;
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.model.bpmn.elements.EndEventDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.HumanTaskDefinition;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;
import io.kikwiflow.model.bpmn.elements.StartEventDefinition;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.end.EndEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.start.StartEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.HumanTaskEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceEntity;

import java.util.Objects;

public final class FlowNodeMapper {

    private FlowNodeMapper() {
        // Utility class
    }

    public static FlowNodeDefinition toSnapshot(FlowNodeDefinitionEntity node) {

        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceEntity) {
            return ServiceTaskMapper.toSnapshot((ServiceEntity) node);

        } else if (node instanceof HumanTaskEntity){
            return HumanTaskMapper.toSnapshot((HumanTaskEntity) node);

        } else if (node instanceof StartEventEntity) {
            return StartEventMapper.toSnapshot((StartEventEntity) node);

        } else if (node instanceof EndEventEntity) {
            return EndEventMapper.toSnapshot((EndEventEntity) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }

    public static FlowNodeDefinition toSnapshot(io.kikwiflow.bpmn.model.FlowNodeDefinition node) {

        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof Service) {
            return ServiceTaskMapper.toSnapshot((Service) node);

        } else if (node instanceof StartEvent) {
            return StartEventMapper.toSnapshot((StartEvent) node);

        } else if (node instanceof EndEvent ) {
            return EndEventMapper.toSnapshot((EndEvent) node);
        } else if(node instanceof HumanTask){
            return HumanTaskMapper.toSnapshot((HumanTask) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }

    public static FlowNodeDefinitionEntity toEntity(FlowNodeDefinition node) {
        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceTaskDefinition) {
            return ServiceTaskMapper.toEntity((ServiceTaskDefinition) node);

        } else if(node instanceof HumanTaskDefinition){
            return HumanTaskMapper.toEntity((HumanTaskDefinition) node);

        }else if (node instanceof StartEventDefinition) {
            return StartEventMapper.toEntity((StartEventDefinition) node);

        } else if (node instanceof EndEventDefinition) {
            return EndEventMapper.toEntity((EndEventDefinition) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }
}