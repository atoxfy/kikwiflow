/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.persistence.mongodb.mapper.definition;

import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ExtensionPropertiesMapper;
import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;

public class VariableDeclarationMapper {

    private VariableDeclarationMapper() {}

    public static List<Document> toDocuments(List<VariableDeclaration> declarations) {
        if (declarations == null) {
            return List.of();
        }
        return declarations.stream().map(VariableDeclarationMapper::toDocument).collect(Collectors.toList());
    }

    public static List<VariableDeclaration> fromDocuments(List<Document> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(VariableDeclarationMapper::fromDocument).collect(Collectors.toList());
    }

    private static Document toDocument(VariableDeclaration declaration) {
        return new Document("key", declaration.key())
                .append("label", declaration.label())
                .append("description", declaration.description())
                .append("format", declaration.format() != null ? declaration.format().name() : null)
                .append("options", declaration.options().stream()
                        .map(o -> new Document("value", o.value()).append("label", o.label()))
                        .collect(Collectors.toList()))
                .append("currency", declaration.currency())
                .append("validationRegex", declaration.validationRegex())
                .append("validationMessage", declaration.validationMessage())
                .append("extensionProperties", declaration.extensionProperties() != null
                        ? new Document(declaration.extensionProperties()) : null);
    }

    private static VariableDeclaration fromDocument(Document doc) {
        String format = doc.getString("format");
        List<Document> options = doc.getList("options", Document.class);
        Document extensionProperties = doc.get("extensionProperties", Document.class);

        return VariableDeclaration.builder()
                .key(doc.getString("key"))
                .label(doc.getString("label"))
                .description(doc.getString("description"))
                .format(format != null ? VariableFormat.valueOf(format) : null)
                .options(options == null ? List.of() : options.stream()
                        .map(o -> new VariableOption(o.getString("value"), o.getString("label")))
                        .collect(Collectors.toList()))
                .currency(doc.getString("currency"))
                .validationRegex(doc.getString("validationRegex"))
                .validationMessage(doc.getString("validationMessage"))
                .extensionProperties(extensionProperties != null ? ExtensionPropertiesMapper.mapToDefinition(extensionProperties) : null)
                .build();
    }
}
