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

package io.kikwiflow.model.definition.process.elements;

import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateDefinition;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.node.WaitState;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Nó de espera reativa por correlação externa (webhook/mensagem assíncrona). Em modo {@code STANDALONE}
 * espera uma única chave; em modo {@code GROUP} espera N chaves (scatter-gather), concluindo de acordo com
 * {@code matchPolicy}.
 */
public record EventCatcherDefinition(String id,
                                     String name,
                                     String type,
                                     String description,
                                     Boolean commitAfter,
                                     Boolean commitBefore,
                                     List<SequenceFlowDefinition> outgoing,
                                     List<String> boundaryEventIds,
                                     Map<String, String> extensionProperties,
                                     LayoutCoordinates layout,
                                     CatchType catchType,
                                     CorrelationProviderType providerType,
                                     String providerBean,
                                     String providerVariable,
                                     String staticKey,
                                     String keyPrefix,
                                     String keySuffix,
                                     String displayNamePrefix,
                                     String displayNameSuffix,
                                     MatchPolicy matchPolicy,
                                     List<CorrelationTemplateDefinition> correlationTemplates) implements FlowNodeDefinition, WaitState, CorrelationKeySource {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Boolean commitAfter;
        private Boolean commitBefore;
        private List<SequenceFlowDefinition> outgoing = Collections.emptyList();
        private List<String> boundaryEventIds = Collections.emptyList();
        private Map<String, String> extensionProperties;
        private LayoutCoordinates layout;
        private CatchType catchType;
        private CorrelationProviderType providerType;
        private String providerBean;
        private String providerVariable;
        private String staticKey;
        private String keyPrefix;
        private String keySuffix;
        private String displayNamePrefix;
        private String displayNameSuffix;
        private MatchPolicy matchPolicy;
        private List<CorrelationTemplateDefinition> correlationTemplates;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
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

        public Builder extensionProperties(Map<String, String> extensionProperties) {
            this.extensionProperties = extensionProperties;
            return this;
        }

        public Builder layout(LayoutCoordinates layout) {
            this.layout = layout;
            return this;
        }

        public Builder catchType(CatchType catchType) {
            this.catchType = catchType;
            return this;
        }

        public Builder providerType(CorrelationProviderType providerType) {
            this.providerType = providerType;
            return this;
        }

        public Builder providerBean(String providerBean) {
            this.providerBean = providerBean;
            return this;
        }

        public Builder providerVariable(String providerVariable) {
            this.providerVariable = providerVariable;
            return this;
        }

        public Builder staticKey(String staticKey) {
            this.staticKey = staticKey;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public Builder keySuffix(String keySuffix) {
            this.keySuffix = keySuffix;
            return this;
        }

        public Builder displayNamePrefix(String displayNamePrefix) {
            this.displayNamePrefix = displayNamePrefix;
            return this;
        }

        public Builder displayNameSuffix(String displayNameSuffix) {
            this.displayNameSuffix = displayNameSuffix;
            return this;
        }

        public Builder matchPolicy(MatchPolicy matchPolicy) {
            this.matchPolicy = matchPolicy;
            return this;
        }

        public Builder correlationTemplates(List<CorrelationTemplateDefinition> correlationTemplates) {
            this.correlationTemplates = correlationTemplates;
            return this;
        }

        public EventCatcherDefinition build() {
            return new EventCatcherDefinition(id, name, "EVENT_CATCHER", description, commitAfter, commitBefore,
                    outgoing, boundaryEventIds, extensionProperties, layout, catchType, providerType, providerBean,
                    providerVariable, staticKey, keyPrefix, keySuffix, displayNamePrefix, displayNameSuffix,
                    matchPolicy, correlationTemplates);
        }
    }
}
