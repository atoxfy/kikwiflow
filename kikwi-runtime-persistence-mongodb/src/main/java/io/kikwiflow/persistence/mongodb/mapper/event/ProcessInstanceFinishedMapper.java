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

import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessVariableMapper;
import io.kikwiflow.persistence.mongodb.util.MongoKeyEncoder;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProcessInstanceFinishedMapper {

    private ProcessInstanceFinishedMapper() {}

    public static Document toDocument(ProcessInstanceFinished event) {
        Document doc = new Document("id", event.getId())
                .append("businessKey", event.getBusinessKey())
                .append("status", event.getStatus() != null ? event.getStatus().name() : null)
                .append("processDefinitionId", event.getProcessDefinitionId())
                .append("processDefinitionKey", event.getProcessDefinitionKey())
                .append("processDefinitionVersion", event.getProcessDefinitionVersion())
                .append("startedAt", event.getStartedAt() != null ? java.util.Date.from(event.getStartedAt()) : null)
                .append("endedAt", event.getEndedAt() != null ? java.util.Date.from(event.getEndedAt()) : null)
                .append("businessValue", event.getBusinessValue() != null ? new Decimal128(event.getBusinessValue()) : null)
                .append("tenantId", event.getTenantId())
                .append("origin", event.getOrigin())
                .append("parentInstanceId", event.getParentInstanceId())
                .append("callerTaskId", event.getCallerTaskId())
                .append("callerBranchId", event.getCallerBranchId());

        if (event.getVariables() != null) {
            Document variablesDoc = new Document();
            event.getVariables().forEach((key, variable) ->
                    variablesDoc.put(MongoKeyEncoder.encode(key), ProcessVariableMapper.toDocument(variable)));
            doc.append("variables", variablesDoc);
        }

        return doc;
    }

    public static ProcessInstanceFinished fromDocument(Document doc) {
        Map<String, ProcessVariable> variables = Collections.emptyMap();
        Document variablesDoc = doc.get("variables", Document.class);
        if (variablesDoc != null) {
            variables = variablesDoc.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> MongoKeyEncoder.decode(entry.getKey()),
                            entry -> ProcessVariableMapper.fromDocumentToVariable((Document) entry.getValue())
                    ));
        }

        return ProcessInstanceFinished.builder()
                .id(doc.getString("id"))
                .businessKey(doc.getString("businessKey"))
                .status(doc.getString("status") != null ? ProcessInstanceStatus.valueOf(doc.getString("status")) : null)
                .processDefinitionId(doc.getString("processDefinitionId"))
                .processDefinitionKey(doc.getString("processDefinitionKey"))
                .processDefinitionVersion(doc.getInteger("processDefinitionVersion"))
                .startedAt(InstantMapper.mapToInstant("startedAt", doc))
                .endedAt(InstantMapper.mapToInstant("endedAt", doc))
                .businessValue(doc.get("businessValue") != null ? doc.get("businessValue", Decimal128.class).bigDecimalValue() : null)
                .tenantId(doc.getString("tenantId"))
                .origin(doc.getString("origin"))
                .parentInstanceId(doc.getString("parentInstanceId"))
                .callerTaskId(doc.getString("callerTaskId"))
                .callerBranchId(doc.getString("callerBranchId"))
                .variables(variables)
                .build();
    }
}
