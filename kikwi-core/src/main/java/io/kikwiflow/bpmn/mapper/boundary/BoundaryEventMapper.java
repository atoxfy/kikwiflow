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

package io.kikwiflow.bpmn.mapper.boundary;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.boundary.BoundaryEvent;
import io.kikwiflow.bpmn.model.boundary.InterruptiveTimerBoundaryEvent;
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.model.definition.process.elements.BoundaryEventDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BoundaryEventMapper {

    private BoundaryEventMapper() {
        // Utility class
    }

    public static List<BoundaryEventDefinition> toSnapshot(List<BoundaryEvent> events){
        if(Objects.isNull(events)){
            return Collections.emptyList();
        }

        return events.stream()
                .map(BoundaryEventMapper::toSnapshot)
                .toList();
    }

    public static BoundaryEventDefinition toSnapshot(BoundaryEvent event){
        if (Objects.isNull(event)) {
            return null;
        }

        if(event instanceof InterruptiveTimerBoundaryEvent interruptiveTimerBoundaryEvent){
            return InterruptiveTimerEventDefinition.builder()
                    .id(interruptiveTimerBoundaryEvent.getId())
                    .name(interruptiveTimerBoundaryEvent.getName())
                    .description(interruptiveTimerBoundaryEvent.getDescription())
                    .commitAfter(interruptiveTimerBoundaryEvent.getCommitAfter())
                    .commitBefore(interruptiveTimerBoundaryEvent.getCommitBefore())
                    .outgoing(SequenceFlowMapper.toSnapshot(interruptiveTimerBoundaryEvent.getOutgoing()))
                    .attachedToRef(interruptiveTimerBoundaryEvent.getAttachedToRef())
                    .duration(interruptiveTimerBoundaryEvent.getDuration())
                    .extensionProperties(event.getExtensionProperties())
                    .build();
        }

        throw new NotImplementedException("BoundaryEvent mapping not implemented yet");

    }
}
