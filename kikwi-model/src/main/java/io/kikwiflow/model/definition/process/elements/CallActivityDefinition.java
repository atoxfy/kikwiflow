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

package io.kikwiflow.model.definition.process.elements;

import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import io.kikwiflow.model.execution.enumerated.CallActivityIterationMode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Nó {@code CALL_ACTIVITY_COORDINATOR} — não implementa {@link io.kikwiflow.model.execution.node.Executable}:
 * não tem handler de negócio próprio (como {@link JoinGatewayDefinition}/{@link ParallelGatewayDefinition}),
 * o fan-out (coordenadora + N iniciadoras) é montado inteiramente em {@code ContinuationService}, e a
 * retomada da própria coordenadora (após {@code pendingBranchIds} esvaziar) segue pelo caminho genérico de
 * {@code ProcessExecutionManager}/{@code Navigator} sem executar handler nenhum — mesmo motivo de
 * {@code TimerTaskDefinition} não ser {@code WaitState} (ver seu Javadoc).
 */
public record CallActivityDefinition(String id,
                                     String name,
                                     String type,
                                     String description,
                                     Boolean commitAfter,
                                     Boolean commitBefore,
                                     List<SequenceFlowDefinition> outgoing,
                                     List<String> boundaryEventIds,
                                     Map<String, String> extensionProperties,
                                     LayoutCoordinates layout,
                                     String calledElement,
                                     String collectionVariable,
                                     String elementVariable,
                                     CallActivityIterationMode iterationMode) implements FlowNodeDefinition {


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Boolean commitAfter;
        private Boolean commitBefore;
        private LayoutCoordinates layout;
        private List<SequenceFlowDefinition> outgoing = Collections.emptyList();
        private List<String> boundaryEventIds = Collections.emptyList();
        private Map<String, String> extensionProperties;

        private String calledElement;
        private String collectionVariable;
        private String elementVariable;
        private CallActivityIterationMode iterationMode;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder calledElement(String calledElement) {
            this.calledElement = calledElement;
            return this;
        }

        public Builder collectionVariable(String collectionVariable) {
            this.collectionVariable = collectionVariable;
            return this;
        }

        public Builder elementVariable(String elementVariable) {
            this.elementVariable = elementVariable;
            return this;
        }

        public Builder iterationMode(CallActivityIterationMode iterationMode) {
            this.iterationMode = iterationMode;
            return this;
        }

        public Builder layout(LayoutCoordinates layout) {
            this.layout = layout;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder commitAfter(Boolean commitAfter) {
            this.commitAfter = commitAfter;
            return this;
        }

        public Builder commitBefore(Boolean commitBefore) {
            this.commitBefore = commitBefore;
            return this;
        }

        public Builder outgoing(List<SequenceFlowDefinition> outgoing) {
            if (outgoing != null) {
                this.outgoing = outgoing;
            }
            return this;
        }

        public Builder boundaryEventIds(List<String> boundaryEventIds) {
            if (boundaryEventIds != null) {
                this.boundaryEventIds = boundaryEventIds;
            }
            return this;
        }


        public Builder extensionProperties(Map<String, String> extensionProperties){
            this.extensionProperties = extensionProperties;
            return this;
        }

        public CallActivityDefinition build() {
            return new CallActivityDefinition(id,
                    name,
                    "CALL_ACTIVITY_COORDINATOR",
                    description,
                    commitAfter,
                    commitBefore,
                    outgoing,
                    boundaryEventIds,
                    extensionProperties,
                    layout,
                    calledElement,
                    collectionVariable,
                    elementVariable,
                    iterationMode);
        }
    }
}
