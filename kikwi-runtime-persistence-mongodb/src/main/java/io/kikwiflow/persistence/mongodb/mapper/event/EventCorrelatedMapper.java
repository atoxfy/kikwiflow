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

import io.kikwiflow.model.event.EventCorrelated;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessVariableMapper;
import io.kikwiflow.persistence.mongodb.util.MongoKeyEncoder;
import org.bson.Document;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public final class EventCorrelatedMapper {

    private EventCorrelatedMapper() {}

    public static Document toDocument(EventCorrelated event) {
        Document variablesDoc = new Document();
        event.submittedVariables().forEach((key, variable) ->
                variablesDoc.put(MongoKeyEncoder.encode(key), ProcessVariableMapper.toDocument(variable)));

        return new Document("correlationKey", event.correlationKey())
                .append("externalTaskId", event.externalTaskId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("actorId", event.actorId())
                .append("correlatedAt", event.correlatedAt() != null ? java.util.Date.from(event.correlatedAt()) : null)
                .append("submittedVariables", variablesDoc);
    }

    public static EventCorrelated fromDocument(Document doc) {
        Document variablesDoc = doc.get("submittedVariables", Document.class);
        Map<String, ProcessVariable> variables = variablesDoc == null ? Collections.emptyMap()
                : variablesDoc.entrySet().stream().collect(Collectors.toMap(
                        entry -> MongoKeyEncoder.decode(entry.getKey()),
                        entry -> ProcessVariableMapper.fromDocumentToVariable((Document) entry.getValue())));

        return new EventCorrelated(
                doc.getString("correlationKey"),
                doc.getString("externalTaskId"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("taskDefinitionId"),
                doc.getString("actorId"),
                InstantMapper.mapToInstant("correlatedAt", doc),
                variables
        );
    }
}
