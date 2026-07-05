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

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = KKFInterruptiveTimerEventDefinition.class, name = "INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = KKFInterruptiveTimerEventDefinition.class, name = "NON_INTERRUPTIVE_TIMER"),
        @JsonSubTypes.Type(value = KKFErrorHandlerDefinition.class, name = "ERROR_HANDLER"),


})
public sealed interface KKFBoundaryEventDefinition extends KKFFlowNodeDefinition permits KKFNonInterruptiveTimerEventDefinition, KKFInterruptiveTimerEventDefinition, KKFErrorHandlerDefinition {
     String attachedToRef();
}
