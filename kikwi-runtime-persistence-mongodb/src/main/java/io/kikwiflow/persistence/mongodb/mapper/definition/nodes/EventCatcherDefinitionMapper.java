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

import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import org.bson.Document;

public class EventCatcherDefinitionMapper {

    public static EventCatcherDefinition mapToDefinition(Document doc) {

        String catchTypeStr = doc.getString("catchType");
        String providerTypeStr = doc.getString("providerType");
        String matchPolicyStr = doc.getString("matchPolicy");

        return EventCatcherDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .catchType(catchTypeStr != null ? CatchType.valueOf(catchTypeStr) : null)
                .providerType(providerTypeStr != null ? CorrelationProviderType.valueOf(providerTypeStr) : null)
                .providerBean(doc.getString("providerBean"))
                .providerVariable(doc.getString("providerVariable"))
                .staticKey(doc.getString("staticKey"))
                .keyPrefix(doc.getString("keyPrefix"))
                .keySuffix(doc.getString("keySuffix"))
                .displayNamePrefix(doc.getString("displayNamePrefix"))
                .displayNameSuffix(doc.getString("displayNameSuffix"))
                .matchPolicy(matchPolicyStr != null ? MatchPolicy.valueOf(matchPolicyStr) : null)
                .correlationTemplates(CorrelationTemplateMapper.mapToDefinitionList(doc))
                .boundaryEventIds(doc.getList("boundaryEventIds", String.class))
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .layout(LayoutCoordinatesMapper.mapToDefinition(doc.get("layout", Document.class)))
                .build();
    }

    public static void mapToDocument(Document doc, EventCatcherDefinition ec) {
        doc.append("catchType", ec.catchType() != null ? ec.catchType().name() : null);
        doc.append("providerType", ec.providerType() != null ? ec.providerType().name() : null);
        doc.append("providerBean", ec.providerBean());
        doc.append("providerVariable", ec.providerVariable());
        doc.append("staticKey", ec.staticKey());
        doc.append("keyPrefix", ec.keyPrefix());
        doc.append("keySuffix", ec.keySuffix());
        doc.append("displayNamePrefix", ec.displayNamePrefix());
        doc.append("displayNameSuffix", ec.displayNameSuffix());
        doc.append("matchPolicy", ec.matchPolicy() != null ? ec.matchPolicy().name() : null);
        if (ec.correlationTemplates() != null) {
            doc.append("correlationTemplates", CorrelationTemplateMapper.toDocumentList(ec.correlationTemplates()));
        }
        if (ec.boundaryEventIds() != null) {
            doc.append("boundaryEventIds", ec.boundaryEventIds());
        }
    }
}
