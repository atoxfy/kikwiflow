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

import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessVariableMapper;
import io.kikwiflow.persistence.mongodb.util.MongoKeyEncoder;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProcessInstanceStartedMapper {

    private ProcessInstanceStartedMapper() {}

    public static Document toDocument(ProcessInstanceStarted event) {
        Document doc = new Document("id", event.id())
                .append("businessKey", event.businessKey())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processDefinitionKey", event.processDefinitionKey())
                .append("processDefinitionVersion", event.processDefinitionVersion())
                .append("startedAt", event.startedAt() != null ? java.util.Date.from(event.startedAt()) : null)
                .append("businessValue", event.businessValue() != null ? new Decimal128(event.businessValue()) : null)
                .append("tenantId", event.tenantId())
                .append("origin", event.origin())
                .append("parentInstanceId", event.parentInstanceId())
                .append("callerTaskId", event.callerTaskId())
                .append("callerBranchId", event.callerBranchId())
                .append("actorId", event.actorId());

        if (event.variables() != null) {
            Document variablesDoc = new Document();
            event.variables().forEach((key, variable) ->
                    variablesDoc.put(MongoKeyEncoder.encode(key), ProcessVariableMapper.toDocument(variable)));
            doc.append("variables", variablesDoc);
        }

        return doc;
    }

    public static ProcessInstanceStarted fromDocument(Document doc) {
        Map<String, ProcessVariable> variables = Collections.emptyMap();
        Document variablesDoc = doc.get("variables", Document.class);
        if (variablesDoc != null) {
            variables = variablesDoc.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> MongoKeyEncoder.decode(entry.getKey()),
                            entry -> ProcessVariableMapper.fromDocumentToVariable((Document) entry.getValue())
                    ));
        }

        return new ProcessInstanceStarted(
                doc.getString("id"),
                doc.getString("businessKey"),
                doc.getString("processDefinitionId"),
                doc.getString("processDefinitionKey"),
                doc.getInteger("processDefinitionVersion"),
                variables,
                InstantMapper.mapToInstant("startedAt", doc),
                doc.get("businessValue") != null ? doc.get("businessValue", Decimal128.class).bigDecimalValue() : null,
                doc.getString("tenantId"),
                doc.getString("origin"),
                doc.getString("parentInstanceId"),
                doc.getString("callerTaskId"),
                doc.getString("callerBranchId"),
                doc.getString("actorId")
        );
    }
}
