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
package io.kikwiflow.persistence.mongodb.mapper.definition.nodes;

import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import org.bson.Document;

public class AgentTaskDefinitionMapper {

    public static AgentTaskDefinition mapToDefinition(Document doc) {
        Document agentRef = doc.get("agentRef", Document.class);
        Document limits = doc.get("limits", Document.class);

        return AgentTaskDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .boundaryEventIds(doc.getList("boundaryEventIds", String.class))
                .layout(LayoutCoordinatesMapper.mapToDefinition(doc.get("layout", Document.class)))
                .variableBindings(VariableBindingsMapper.mapToDefinition(doc.get("variableBindings", Document.class)))
                .topic(doc.getString("topic"))
                .agentRef(agentRef != null
                        ? new AgentTaskDefinition.AgentRef(agentRef.getString("key"), agentRef.getInteger("version"))
                        : null)
                .tools(doc.getList("tools", String.class))
                .limits(limits != null
                        ? new AgentTaskDefinition.AgentLimits(limits.getInteger("maxTurns"), limits.getString("ttl"))
                        : null)
                .build();
    }

    public static void toDocument(Document doc, AgentTaskDefinition agentTask) {
        doc.append("boundaryEventIds", agentTask.boundaryEventIds());
        doc.append("topic", agentTask.topic());
        if (agentTask.agentRef() != null) {
            doc.append("agentRef", new Document("key", agentTask.agentRef().key())
                    .append("version", agentTask.agentRef().version()));
        }
        doc.append("tools", agentTask.tools());
        if (agentTask.limits() != null) {
            doc.append("limits", new Document("maxTurns", agentTask.limits().maxTurns())
                    .append("ttl", agentTask.limits().ttl()));
        }
    }
}
