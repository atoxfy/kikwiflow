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

import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;


public final class ProcessInstanceMapper {

    private ProcessInstanceMapper() {}

    public static Document toDocument(ProcessInstance instance) {
        if (instance == null) return null;

        Document doc = new Document("_id", instance.id())
                .append("businessKey", instance.businessKey())
                .append("status", instance.status().name())
                .append("processDefinitionId", instance.processDefinitionId())
                .append("tenantId", instance.tenantId())
                .append("startedAt", instance.startedAt())
                .append("endedAt", instance.endedAt())
                .append("origin", instance.origin());

        if (instance.businessValue() != null) {
            doc.append("businessValue", new Decimal128(instance.businessValue()));
        }

        if (instance.variables() != null) {
            Document variablesDoc = new Document();
            instance.variables().forEach((key, variable) ->
                    variablesDoc.append(key, ProcessVariableMapper.toDocument(variable))
            );
            doc.append("variables", variablesDoc);
        }

        return doc;
    }

    public static ProcessInstance fromDocument(Document doc) {
        if (doc == null) return null;

        BigDecimal businessValue = null;
        if (doc.get("businessValue") != null) {
            businessValue = doc.get("businessValue", Decimal128.class).bigDecimalValue();
        }

        Map<String, ProcessVariable> variables = Collections.emptyMap();
        Document variablesDoc = doc.get("variables", Document.class);
        if (variablesDoc != null) {
            variables = variablesDoc.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> ProcessVariableMapper.fromDocumentToVariable((Document) entry.getValue())
                    ));
        }

        return ProcessInstance.builder()
                .id(doc.getString("_id"))
                .businessKey(doc.getString("businessKey"))
                .businessValue(businessValue)
                .tenantId(doc.getString("tenantId"))
                .status(ProcessInstanceStatus.valueOf(doc.getString("status")))
                .processDefinitionId(doc.getString("processDefinitionId"))
                .variables(variables)
                .startedAt(doc.get("startedAt", Instant.class))
                .endedAt(doc.get("endedAt", Instant.class))
                .origin(doc.getString("origin"))
                .build();
    }


}
