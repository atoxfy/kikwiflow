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

import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;
import org.bson.Document;

public class InterruptiveTimerEventDefinitionMapper {

    public static InterruptiveTimerEventDefinition mapToDefinition(Document doc) {

        return InterruptiveTimerEventDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .attachedToRef(doc.getString("attachedToRef"))
                .staticValue(doc.getString("staticValue"))
                .providerBean(doc.getString("providerBean"))
                .providerVariable(doc.getString("providerVariable"))
                .providerType(TimeProviderType.valueOf(doc.getString("providerType")))//TODO ADICIONAR VALIDAÇÃO NO VALIDATOR.
                .extensionProperties(ExtensionPropertiesMapper.mapToDefinition(doc.get("extensionProperties", Document.class)))
                .outgoing(SequenceFlowMapper.mapToDefinitionList(doc))
                .build();
    }

    public static void mapToDocument(Document doc, InterruptiveTimerEventDefinition te){
        doc.append("attachedToRef", te.attachedToRef());
        doc.append("staticValue", te.staticValue());
        doc.append("providerBean", te.providerBean());
        doc.append("providerVariable", te.providerVariable());
        doc.append("providerType", te.providerType().name());
    }
}
