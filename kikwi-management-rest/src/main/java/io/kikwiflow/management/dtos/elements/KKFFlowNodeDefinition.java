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

package io.kikwiflow.management.dtos.elements;



import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kikwiflow.management.dtos.layout.KKFLayoutCoordinates;

import java.util.List;
import java.util.Map;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = KKFStartEventDefinition.class, name = "DEFAULT_START_EVENT"),
        @JsonSubTypes.Type(value = KKFEndEventDefinition.class, name = "DEFAULT_END_EVENT"),
        @JsonSubTypes.Type(value = KKFExclusiveGatewayDefinition.class, name = "EXCLUSIVE_GATEWAY"),
        @JsonSubTypes.Type(value = KKFParallelGatewayDefinition.class, name = "PARALLEL_GATEWAY"),
        @JsonSubTypes.Type(value = KKFJoinGatewayDefinition.class, name = "JOIN_GATEWAY"),
        @JsonSubTypes.Type(value = KKFExternalTaskDefinition.class, name = "EXTERNAL_TASK"),
        @JsonSubTypes.Type(value = KKFExecutableTaskDefinition.class, name = "EXECUTABLE_TASK"),
        @JsonSubTypes.Type(value = KKFInterruptiveTimerEventDefinition.class, name = "INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = KKFNonInterruptiveTimerEventDefinition.class, name = "NON_INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = KKFErrorHandlerDefinition.class, name = "ERROR_HANDLER"),
        @JsonSubTypes.Type(value = KKFEventCatcherDefinition.class, name = "EVENT_CATCHER"),
        @JsonSubTypes.Type(value = KKFInterruptiveCatchEventDefinition.class, name = "BOUNDARY_INTERRUPTIVE_CATCH_EVENT"),
        @JsonSubTypes.Type(value = KKFEventThrowerDefinition.class, name = "EVENT_THROWER"),
        @JsonSubTypes.Type(value = KKFCallActivityDefinition.class, name = "CALL_ACTIVITY_COORDINATOR"),
        @JsonSubTypes.Type(value = KKFTimerTaskDefinition.class, name = "TIMER_TASK")

})
public sealed interface KKFFlowNodeDefinition permits KKFStartEventDefinition, KKFExternalTaskDefinition, KKFExecutableTaskDefinition, KKFEndEventDefinition, KKFExclusiveGatewayDefinition, KKFInterruptiveTimerEventDefinition, KKFJoinGatewayDefinition, KKFParallelGatewayDefinition, KKFNonInterruptiveTimerEventDefinition, KKFErrorHandlerDefinition, KKFBoundaryEventDefinition, KKFEventCatcherDefinition, KKFInterruptiveCatchEventDefinition, KKFEventThrowerDefinition, KKFCallActivityDefinition, KKFTimerTaskDefinition {
    String id();
    String name();
    String type();
    String description();
    Boolean commitAfter();
    Boolean commitBefore();
    List<KKFSequenceFlowDefinition> outgoing();
    Map<String, String> extensionProperties();
    KKFLayoutCoordinates layout();
}
