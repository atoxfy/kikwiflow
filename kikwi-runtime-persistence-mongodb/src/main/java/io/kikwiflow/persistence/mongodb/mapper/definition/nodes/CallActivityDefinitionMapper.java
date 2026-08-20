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

import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.execution.enumerated.CallActivityIterationMode;
import org.bson.Document;

/**
 * Antes deste mapper existir, {@code CallActivityDefinition} não tinha nenhuma entrada em
 * {@code ProcessDefinitionMapper} — {@code toDocument} descartava silenciosamente todos os campos específicos
 * do nó (caía no {@code default -> {}} do switch) e {@code fromDocument} lançava
 * {@code IllegalArgumentException: Tipo de FlowNode desconhecido} ao tentar recarregar qualquer processo com
 * um nó {@code CALL_ACTIVITY_COORDINATOR} a partir do MongoDB — mesmo bug já corrigido para
 * {@code EventCatcherDefinition}/{@code InterruptiveCatchEventDefinition} (ver
 * {@link EventCatcherDefinitionMapper}, docs/engine/17-boundary-interruptive-catch-event.md).
 */
public class CallActivityDefinitionMapper {

    public static CallActivityDefinition mapToDefinition(Document doc) {

        String iterationModeStr = doc.getString("iterationMode");

        return CallActivityDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .calledElement(doc.getString("calledElement"))
                .collectionVariable(doc.getString("collectionVariable"))
                .elementVariable(doc.getString("elementVariable"))
                .iterationMode(iterationModeStr != null ? CallActivityIterationMode.valueOf(iterationModeStr) : null)
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .boundaryEventIds(doc.getList("boundaryEventIds", String.class))
                .layout(LayoutCoordinatesMapper.mapToDefinition(doc.get("layout", Document.class)))
                .build();
    }

    public static void mapToDocument(Document doc, CallActivityDefinition ca) {
        doc.append("calledElement", ca.calledElement());
        doc.append("collectionVariable", ca.collectionVariable());
        doc.append("elementVariable", ca.elementVariable());
        doc.append("iterationMode", ca.iterationMode() != null ? ca.iterationMode().name() : null);

        if (ca.boundaryEventIds() != null) {
            doc.append("boundaryEventIds", ca.boundaryEventIds());
        }
    }
}
