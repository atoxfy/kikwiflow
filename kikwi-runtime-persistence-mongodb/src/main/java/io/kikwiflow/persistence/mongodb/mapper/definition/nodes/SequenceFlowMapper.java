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

import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SequenceFlowMapper {

    public static List<SequenceFlowDefinition> mapToDefinitionList(Document doc) {

        List<Document> outgoingDocs = doc.getList("outgoing", Document.class, Collections.emptyList());

        return outgoingDocs.stream()
                .map(SequenceFlowMapper::mapToDefinition)
                .collect(Collectors.toList());
    }

    public static SequenceFlowDefinition mapToDefinition(Document flowDoc) {

        if (flowDoc == null) return null;
        List<Document> rawHandlers = flowDoc.getList("positionHandlers", Document.class);

        List<LayoutCoordinates> positionHandlers = (rawHandlers == null)
                ? Collections.emptyList()
                : rawHandlers.stream()
                .map(h -> new LayoutCoordinates(
                        h.get("x", Number.class).doubleValue(),
                        h.get("y", Number.class).doubleValue()
                ))
                .collect(Collectors.toList());

        Map<String, String> extensionProperties = ExtensionPropertiesMapper.mapToDefinition(
                flowDoc.get("extensionProperties", Document.class));

        Document labelPositionDoc = flowDoc.get("labelPosition", Document.class);
        LayoutCoordinates labelPosition = labelPositionDoc == null
                ? null
                : new LayoutCoordinates(
                        labelPositionDoc.get("x", Number.class).doubleValue(),
                        labelPositionDoc.get("y", Number.class).doubleValue());

        return new SequenceFlowDefinition(
                flowDoc.getString("id"),
                flowDoc.getString("name"),
                flowDoc.getString("description"),
                flowDoc.getString("expectedAnswer"),
                flowDoc.getString("targetNodeId"),
                flowDoc.getBoolean("isDefault", false),
                flowDoc.getBoolean("handlesNull", false),
                positionHandlers,
                extensionProperties,
                labelPosition,
                flowDoc.getString("sourceHandle")
        );
    }
}
