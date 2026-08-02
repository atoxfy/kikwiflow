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

package io.kikwiflow.persistence.mongodb.mapper.event;

import io.kikwiflow.model.event.ProcessVariableChanged;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessVariableMapper;
import org.bson.Document;

/**
 * Reaproveita {@link ProcessVariableMapper} para o par valor/isTransient — mesma lógica de coerção de tipo
 * (Decimal128/Date/enum) que já é usada para {@code ProcessVariable} em outros lugares do motor.
 */
public final class ProcessVariableChangedMapper {

    private ProcessVariableChangedMapper() {}

    public static Document toDocument(ProcessVariableChanged event) {
        ProcessVariable variable = new ProcessVariable(event.name(), event.isTransient(), event.value());

        return new Document("processInstanceId", event.processInstanceId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("tenantId", event.tenantId())
                .append("variable", ProcessVariableMapper.toDocument(variable))
                .append("actorId", event.actorId())
                .append("changedAt", event.changedAt() != null ? java.util.Date.from(event.changedAt()) : null);
    }

    public static ProcessVariableChanged fromDocument(Document doc) {
        ProcessVariable variable = ProcessVariableMapper.fromDocumentToVariable(doc.get("variable", Document.class));

        return new ProcessVariableChanged(
                doc.getString("processInstanceId"),
                doc.getString("processDefinitionId"),
                doc.getString("tenantId"),
                variable != null ? variable.name() : null,
                variable != null && variable.isTransient(),
                variable != null ? variable.value() : null,
                doc.getString("actorId"),
                InstantMapper.mapToInstant("changedAt", doc)
        );
    }
}
