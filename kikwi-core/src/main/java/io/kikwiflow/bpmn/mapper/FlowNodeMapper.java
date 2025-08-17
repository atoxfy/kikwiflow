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

import io.kikwiflow.bpmn.mapper.end.EndEventMapper;
import io.kikwiflow.bpmn.mapper.start.StartEventMapper;
import io.kikwiflow.bpmn.mapper.task.HumanTaskMapper;
import io.kikwiflow.bpmn.mapper.task.ServiceTaskMapper;
import io.kikwiflow.bpmn.model.FlowNode;
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.bpmn.model.task.HumanTask;
import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

import java.util.Objects;

public final class FlowNodeMapper {

    private FlowNodeMapper() {
        // Utility class
    }


    public static FlowNodeDefinition toRecord(FlowNode node) {

        if (Objects.isNull(node)) {
            return null;
        }

        if(node instanceof ServiceTask) {
            return ServiceTaskMapper.toSnapshot((ServiceTask) node);

        } else if (node instanceof StartEvent) {
            return StartEventMapper.toSnapshot((StartEvent) node);

        } else if (node instanceof EndEvent ) {
            return EndEventMapper.toSnapshot((EndEvent) node);

        } else if(node instanceof HumanTask){
            return HumanTaskMapper.toSnapshot((HumanTask) node);
        }

        throw new NotImplementedException("Node type " + node.getClass().getSimpleName() + " is not implemented yet");
    }

}