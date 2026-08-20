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
import io.kikwiflow.model.execution.enumerated.TimeProviderType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Nó de fluxo principal que pausa a execução até um {@code dueDate} calculado (STATIC/VARIABLE/BEAN, mesmo
 * mecanismo de {@link InterruptiveTimerEventDefinition}), e então continua pelas suas próprias arestas de
 * saída — o "temporizador" do fluxo principal, em vez de um evento de borda anexado a outro nó.
 *
 * <p>Ao contrário de {@link EventCatcherDefinition}/{@link ExternalTaskDefinition}, este nó <b>não</b>
 * implementa {@code WaitState}: ele é materializado como uma {@code ExecutableTask} (não uma {@code
 * ExternalTask}) e retomado via {@code KikwiflowEngine.executeFromTask(...)}, que reapresenta a própria
 * definição do nó a {@code ProcessExecutionManager.executeFlow(...)} — se fosse {@code WaitState}, a checagem
 * de pausa (que não distingue "primeira vez" de "retomando", ao contrário da checagem de {@code commitBefore})
 * pausaria de novo indefinidamente, nunca avançando para as arestas de saída. A pausa inicial é garantida
 * tratando este nó como sempre {@code commitBefore: true} em {@code ProcessExecutionManager}, independente do
 * valor declarado no {@code .kikwi} — um timer não pode, por natureza, ser executado de forma síncrona.
 *
 * <p>{@code boundaryEventIds} aceita {@code BOUNDARY_INTERRUPTIVE_TIMER}, {@code BOUNDARY_NON_INTERRUPTIVE_TIMER}
 * e {@code BOUNDARY_INTERRUPTIVE_CATCH_EVENT} (não {@code BOUNDARY_ERROR_HANDLER} — este nó não roda handler
 * nenhum, não há try/catch síncrono pra resolver). Seguro por natureza: como este nó não implementa
 * {@code Executable}, não há efeito colateral real em voo pra proteger — a mesma restrição que bloqueia
 * boundary interruptivo num {@code EXECUTABLE_TASK} de verdade ({@code DeployValidator}, ver
 * docs/engine/19-guard-de-finalizacao-boundary-events.md) não se aplica aqui.
 */
public record TimerTaskDefinition(String id,
                                  String name,
                                  String type,
                                  String description,
                                  Boolean commitAfter,
                                  Boolean commitBefore,
                                  List<SequenceFlowDefinition> outgoing,
                                  TimeProviderType providerType,
                                  String providerVariable,
                                  String providerBean,
                                  String staticValue,
                                  List<String> boundaryEventIds,
                                  Map<String, String> extensionProperties,
                                  LayoutCoordinates layout) implements FlowNodeDefinition, TimerDueDateSource {

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
        private TimeProviderType providerType;
        private String providerVariable;
        private String providerBean;
        private String staticValue;
        private List<String> boundaryEventIds = Collections.emptyList();
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

        public Builder providerType(TimeProviderType providerType) {
            this.providerType = providerType;
            return this;
        }

        public Builder providerVariable(String providerVariable) {
            this.providerVariable = providerVariable;
            return this;
        }

        public Builder providerBean(String providerBean) {
            this.providerBean = providerBean;
            return this;
        }

        public Builder staticValue(String staticValue) {
            this.staticValue = staticValue;
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

        public TimerTaskDefinition build() {
            return new TimerTaskDefinition(id, name, "TIMER_TASK", description, commitAfter, commitBefore,
                    outgoing, providerType, providerVariable, providerBean, staticValue, boundaryEventIds,
                    extensionProperties, layout);
        }
    }
}
