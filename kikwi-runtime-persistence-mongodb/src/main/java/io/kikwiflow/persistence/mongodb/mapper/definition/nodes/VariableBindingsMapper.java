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
package io.kikwiflow.persistence.mongodb.mapper.definition.nodes;

import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;

public class VariableBindingsMapper {

    public static VariableBindings mapToDefinition(Document document) {
        if (document == null) {
            return null;
        }
        return new VariableBindings(
                mapBindings(document.getList("inputs", Document.class)),
                mapBindings(document.getList("outputs", Document.class)));
    }

    public static Document toDocument(VariableBindings bindings) {
        if (bindings == null) {
            return null;
        }
        return new Document("inputs", toDocuments(bindings.inputs()))
                .append("outputs", toDocuments(bindings.outputs()));
    }

    private static List<VariableBinding> mapBindings(List<Document> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .map(d -> new VariableBinding(d.getString("variable"), d.getBoolean("required")))
                .collect(Collectors.toList());
    }

    private static List<Document> toDocuments(List<VariableBinding> bindings) {
        return bindings.stream()
                .map(b -> new Document("variable", b.variable()).append("required", b.required()))
                .collect(Collectors.toList());
    }
}
