/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.parser.jackson;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.JoinGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartEventDefinition.class, name = "DEFAULT_START_EVENT"),
        @JsonSubTypes.Type(value = EndEventDefinition.class, name = "DEFAULT_END_EVENT"),
        @JsonSubTypes.Type(value = ExecutableTaskDefinition.class, name = "EXECUTABLE_TASK"),
        @JsonSubTypes.Type(value = ParallelGatewayDefinition.class, name = "PARALLEL_GATEWAY"),
        @JsonSubTypes.Type(value = JoinGatewayDefinition.class, name = "JOIN_GATEWAY"),
        @JsonSubTypes.Type(value = ExternalTaskDefinition.class, name = "EXTERNAL_TASK"),
        @JsonSubTypes.Type(value = ExclusiveGatewayDefinition.class, name = "EXCLUSIVE_GATEWAY"),
        @JsonSubTypes.Type(value = InterruptiveTimerEventDefinition.class, name = "BOUNDARY_INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = NonInterruptiveTimerEventDefinition.class, name = "BOUNDARY_NON_INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = ErrorHandlerDefinition.class, name = "BOUNDARY_ERROR_HANDLER")

})
public interface FlowNodeDefinitionMixin {
}