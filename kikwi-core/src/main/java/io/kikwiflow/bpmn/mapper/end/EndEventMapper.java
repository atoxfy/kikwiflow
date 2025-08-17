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

package io.kikwiflow.bpmn.mapper.end;

import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.model.bpmn.elements.EndEventDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

import java.util.Objects;

public final class EndEventMapper {

    private EndEventMapper() {
        // Utility class
    }

    public static EndEventDefinition toSnapshot(EndEvent node) {
        if (Objects.isNull(node)) {
            return null;
        }

        return EndEventDefinition.builder()
                .id(node.getId())
                .name(node.getName())
                .description(node.getDescription())
                .commitAfter(node.getCommitAfter())
                .commitBefore(node.getCommitBefore())
                .build();
    }
}


