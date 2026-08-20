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

import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class GatewayAnswerResolvedMapper {

    private GatewayAnswerResolvedMapper() {}

    public static Document toDocument(GatewayAnswerResolved event) {
        return new Document("processInstanceId", event.processInstanceId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("tenantId", event.tenantId())
                .append("processDefinitionKey", event.processDefinitionKey())
                .append("gatewayNodeId", event.gatewayNodeId())
                .append("answerProviderType", event.answerProviderType() != null ? event.answerProviderType().name() : null)
                .append("providerBean", event.providerBean())
                .append("providerVariable", event.providerVariable())
                .append("resolvedAnswer", event.resolvedAnswer())
                .append("chosenFlowId", event.chosenFlowId())
                .append("evaluatedAt", event.evaluatedAt() != null ? java.util.Date.from(event.evaluatedAt()) : null);
    }

    public static GatewayAnswerResolved fromDocument(Document doc) {
        return new GatewayAnswerResolved(
                doc.getString("processInstanceId"),
                doc.getString("processDefinitionId"),
                doc.getString("tenantId"),
                doc.getString("processDefinitionKey"),
                doc.getString("gatewayNodeId"),
                doc.getString("answerProviderType") != null ? AnswerProviderType.valueOf(doc.getString("answerProviderType")) : null,
                doc.getString("providerBean"),
                doc.getString("providerVariable"),
                doc.getString("resolvedAnswer"),
                doc.getString("chosenFlowId"),
                InstantMapper.mapToInstant("evaluatedAt", doc)
        );
    }
}
