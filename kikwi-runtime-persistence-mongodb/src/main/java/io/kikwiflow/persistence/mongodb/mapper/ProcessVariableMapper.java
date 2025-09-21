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
package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessVariableVisibility;
import org.bson.Document;

import java.util.Collections;

public final class ProcessVariableMapper {

    private ProcessVariableMapper() {}

    public static Document toDocument(ProcessVariable variable) {
        if (variable == null) return null;
        return new Document("name", variable.name())
                .append("visibility", variable.visibility().name())
                .append("roles", variable.roles())
                .append("value", variable.value())
                .append("_class", variable.value() != null ? variable.value().getClass().getName() : null);
    }

    public static ProcessVariable fromDocumentToVariable(Document doc) {
        if (doc == null) return null;

        return new ProcessVariable(
                doc.getString("name"),
                ProcessVariableVisibility.valueOf(doc.getString("visibility")),
                doc.getList("roles", String.class, Collections.emptyList()),
                doc.get("value")
        );
    }
}