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
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Evento de borda interruptivo que aguarda uma correlação externa (webhook/mensagem assíncrona) — o "EVENT_CATCHER
 * de borda". Ao contrário de {@link EventCatcherDefinition}, sempre resolve exatamente 1 chave de correlação
 * (não existe modo GROUP na borda): assim que essa chave é entregue via
 * {@code KikwiflowEngine.correlateMessage(...)}, o nó pai ({@code attachedToRef}) é cancelado e o fluxo desvia
 * para as arestas de saída deste nó — o mesmo comportamento de {@link InterruptiveTimerEventDefinition}, só que
 * disparado por correlação de negócio em vez de por prazo.
 */
public record InterruptiveCatchEventDefinition(String id,
                                               String name,
                                               String type,
                                               String description,
                                               Boolean commitAfter,
                                               Boolean commitBefore,
                                               List<SequenceFlowDefinition> outgoing,
                                               String attachedToRef,
                                               CorrelationProviderType providerType,
                                               String providerBean,
                                               String providerVariable,
                                               String staticKey,
                                               String keyPrefix,
                                               String keySuffix,
                                               String displayNamePrefix,
                                               String displayNameSuffix,
                                               List<CorrelationTemplateDefinition> correlationTemplates,
                                               Map<String, String> extensionProperties,
                                               LayoutCoordinates layout) implements BoundaryEventDefinition, FlowNodeDefinition, CorrelationKeySource {

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
        private String attachedToRef;
        private CorrelationProviderType providerType;
        private String providerBean;
        private String providerVariable;
        private String staticKey;
        private String keyPrefix;
        private String keySuffix;
        private String displayNamePrefix;
        private String displayNameSuffix;
        private List<CorrelationTemplateDefinition> correlationTemplates;
        private Map<String, String> extensionProperties;
        private LayoutCoordinates layout;

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

        public Builder attachedToRef(String attachedToRef) {
            this.attachedToRef = attachedToRef;
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

        public Builder correlationTemplates(List<CorrelationTemplateDefinition> correlationTemplates) {
            this.correlationTemplates = correlationTemplates;
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

        public InterruptiveCatchEventDefinition build() {
            return new InterruptiveCatchEventDefinition(id, name, "BOUNDARY_INTERRUPTIVE_CATCH_EVENT", description,
                    commitAfter, commitBefore, outgoing, attachedToRef, providerType, providerBean, providerVariable,
                    staticKey, keyPrefix, keySuffix, displayNamePrefix, displayNameSuffix, correlationTemplates,
                    extensionProperties, layout);
        }
    }
}
