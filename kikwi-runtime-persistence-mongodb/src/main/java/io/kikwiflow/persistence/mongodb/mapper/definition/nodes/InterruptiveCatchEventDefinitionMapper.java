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

import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import org.bson.Document;

public class InterruptiveCatchEventDefinitionMapper {

    public static InterruptiveCatchEventDefinition mapToDefinition(Document doc) {

        String providerTypeStr = doc.getString("providerType");

        return InterruptiveCatchEventDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .attachedToRef(doc.getString("attachedToRef"))
                .providerType(providerTypeStr != null ? CorrelationProviderType.valueOf(providerTypeStr) : null)
                .providerBean(doc.getString("providerBean"))
                .providerVariable(doc.getString("providerVariable"))
                .staticKey(doc.getString("staticKey"))
                .keyPrefix(doc.getString("keyPrefix"))
                .keySuffix(doc.getString("keySuffix"))
                .displayNamePrefix(doc.getString("displayNamePrefix"))
                .displayNameSuffix(doc.getString("displayNameSuffix"))
                .correlationTemplates(CorrelationTemplateMapper.mapToDefinitionList(doc))
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .layout(LayoutCoordinatesMapper.mapToDefinition(doc.get("layout", Document.class)))
                .build();
    }

    public static void mapToDocument(Document doc, InterruptiveCatchEventDefinition ice) {
        doc.append("attachedToRef", ice.attachedToRef());
        doc.append("providerType", ice.providerType() != null ? ice.providerType().name() : null);
        doc.append("providerBean", ice.providerBean());
        doc.append("providerVariable", ice.providerVariable());
        doc.append("staticKey", ice.staticKey());
        doc.append("keyPrefix", ice.keyPrefix());
        doc.append("keySuffix", ice.keySuffix());
        doc.append("displayNamePrefix", ice.displayNamePrefix());
        doc.append("displayNameSuffix", ice.displayNameSuffix());
        if (ice.correlationTemplates() != null) {
            doc.append("correlationTemplates", CorrelationTemplateMapper.toDocumentList(ice.correlationTemplates()));
        }
    }
}
