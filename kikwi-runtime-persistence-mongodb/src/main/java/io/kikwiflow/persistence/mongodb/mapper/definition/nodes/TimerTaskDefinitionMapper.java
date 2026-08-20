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

import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;
import org.bson.Document;

public class TimerTaskDefinitionMapper {

    public static TimerTaskDefinition mapToDefinition(Document doc) {

        String providerTypeStr = doc.getString("providerType");

        return TimerTaskDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .providerType(providerTypeStr != null ? TimeProviderType.valueOf(providerTypeStr) : null)
                .providerBean(doc.getString("providerBean"))
                .providerVariable(doc.getString("providerVariable"))
                .staticValue(doc.getString("staticValue"))
                .boundaryEventIds(doc.getList("boundaryEventIds", String.class))
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .layout(LayoutCoordinatesMapper.mapToDefinition(doc.get("layout", Document.class)))
                .build();
    }

    public static void mapToDocument(Document doc, TimerTaskDefinition tt) {
        doc.append("providerType", tt.providerType() != null ? tt.providerType().name() : null);
        doc.append("providerBean", tt.providerBean());
        doc.append("providerVariable", tt.providerVariable());
        doc.append("staticValue", tt.staticValue());
        if (tt.boundaryEventIds() != null) {
            doc.append("boundaryEventIds", tt.boundaryEventIds());
        }
    }
}
