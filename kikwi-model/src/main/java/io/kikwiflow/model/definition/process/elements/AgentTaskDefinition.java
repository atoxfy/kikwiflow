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
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.execution.node.WaitState;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Missão delegada a um agente (docs/engine/31, E1). Wait state: no runtime vira uma {@code ExternalTask} do tipo
 * {@code AGENT} com {@code topicName = topic}, adquirida por um worker com lease ({@code fetchAndLock}). O token
 * só segue quando o worker conclui entregando as variáveis de {@code variableBindings.outputs} — o contrato que
 * o motor valida, porque não confia no LLM.
 *
 * <p>{@code agentRef}, {@code tools} e {@code limits} são opacos para o motor: quem os resolve e aplica é o
 * runtime de agentes. O motor só os valida no deploy (KIKWI-067..070) e os entrega ao worker.
 *
 * @param topic    canal de aquisição do worker (ex.: {@code kikwi.agents})
 * @param agentRef perfil de agente da plataforma; {@code version == null} = a versão publicada vigente
 * @param tools    ids das ferramentas que a missão pode usar
 * @param limits   {@code maxTurns}/{@code ttl}, aplicados pelo runtime; um prazo de negócio é um boundary timer
 */
public record AgentTaskDefinition(String id,
                                  String name,
                                  String type,
                                  String description,
                                  Boolean commitAfter,
                                  Boolean commitBefore,
                                  List<SequenceFlowDefinition> outgoing,
                                  List<String> boundaryEventIds,
                                  Map<String, String> extensionProperties,
                                  LayoutCoordinates layout,
                                  VariableBindings variableBindings,
                                  String topic,
                                  AgentRef agentRef,
                                  List<String> tools,
                                  AgentLimits limits) implements FlowNodeDefinition, WaitState, VariableBindingAware {

    public static final String TYPE = "AGENT_TASK";

    public AgentTaskDefinition {
        outgoing = outgoing != null ? outgoing : Collections.emptyList();
        boundaryEventIds = boundaryEventIds != null ? boundaryEventIds : Collections.emptyList();
        tools = tools != null ? tools : Collections.emptyList();
    }

    /** @param version {@code null} = versão publicada vigente quando a missão é adquirida. */
    public record AgentRef(String key, Integer version) {
    }

    /** @param ttl duração ISO-8601 (ex.: {@code P7D}). */
    public record AgentLimits(Integer maxTurns, String ttl) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Boolean commitAfter;
        private Boolean commitBefore;
        private List<SequenceFlowDefinition> outgoing;
        private List<String> boundaryEventIds;
        private Map<String, String> extensionProperties;
        private LayoutCoordinates layout;
        private VariableBindings variableBindings;
        private String topic;
        private AgentRef agentRef;
        private List<String> tools;
        private AgentLimits limits;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder commitAfter(Boolean commitAfter) { this.commitAfter = commitAfter; return this; }
        public Builder commitBefore(Boolean commitBefore) { this.commitBefore = commitBefore; return this; }
        public Builder outgoing(List<SequenceFlowDefinition> outgoing) { this.outgoing = outgoing; return this; }
        public Builder boundaryEventIds(List<String> boundaryEventIds) { this.boundaryEventIds = boundaryEventIds; return this; }
        public Builder extensionProperties(Map<String, String> extensionProperties) { this.extensionProperties = extensionProperties; return this; }
        public Builder layout(LayoutCoordinates layout) { this.layout = layout; return this; }
        public Builder variableBindings(VariableBindings variableBindings) { this.variableBindings = variableBindings; return this; }
        public Builder topic(String topic) { this.topic = topic; return this; }
        public Builder agentRef(AgentRef agentRef) { this.agentRef = agentRef; return this; }
        public Builder tools(List<String> tools) { this.tools = tools; return this; }
        public Builder limits(AgentLimits limits) { this.limits = limits; return this; }

        public AgentTaskDefinition build() {
            return new AgentTaskDefinition(id, name, TYPE, description, commitAfter, commitBefore, outgoing,
                    boundaryEventIds, extensionProperties, layout, variableBindings, topic, agentRef, tools, limits);
        }
    }
}
