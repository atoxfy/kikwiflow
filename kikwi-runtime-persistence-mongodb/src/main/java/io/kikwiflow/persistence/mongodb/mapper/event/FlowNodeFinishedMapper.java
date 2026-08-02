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

import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import org.bson.Document;

public final class FlowNodeFinishedMapper {

    private FlowNodeFinishedMapper() {}

    public static Document toDocument(FlowNodeFinished event) {
        return new Document("flowNodeDefinitionId", event.getFlowNodeDefinitionId())
                .append("flowNodeType", event.getFlowNodeType())
                .append("flowNodeName", event.getFlowNodeName())
                .append("flowNodeDescription", event.getFlowNodeDescription())
                .append("processInstanceId", event.getProcessInstanceId())
                .append("processDefinitionId", event.getProcessDefinitionId())
                .append("tenantId", event.tenantId())
                .append("processDefinitionKey", event.getProcessDefinitionKey())
                .append("interruptedByNodeDefinitionId", event.getInterruptedByNodeDefinitionId())
                .append("startedAt", event.getStartedAt() != null ? java.util.Date.from(event.getStartedAt()) : null)
                .append("finishedAt", event.getFinishedAt() != null ? java.util.Date.from(event.getFinishedAt()) : null)
                .append("nodeExecutionStatus", event.getNodeExecutionStatus() != null ? event.getNodeExecutionStatus().name() : null)
                .append("errorType", event.getErrorType())
                .append("errorMessage", event.getErrorMessage())
                .append("errorStackTrace", event.getErrorStackTrace());
    }

    public static FlowNodeFinished fromDocument(Document doc) {
        return FlowNodeFinished.builder()
                .flowNodeDefinitionId(doc.getString("flowNodeDefinitionId"))
                .flowNodeType(doc.getString("flowNodeType"))
                .flowNodeName(doc.getString("flowNodeName"))
                .flowNodeDescription(doc.getString("flowNodeDescription"))
                .processInstanceId(doc.getString("processInstanceId"))
                .processDefinitionId(doc.getString("processDefinitionId"))
                .tenantId(doc.getString("tenantId"))
                .processDefinitionKey(doc.getString("processDefinitionKey"))
                .interruptedByNodeDefinitionId(doc.getString("interruptedByNodeDefinitionId"))
                .startedAt(InstantMapper.mapToInstant("startedAt", doc))
                .finishedAt(InstantMapper.mapToInstant("finishedAt", doc))
                .nodeExecutionStatus(doc.getString("nodeExecutionStatus") != null
                        ? NodeExecutionStatus.valueOf(doc.getString("nodeExecutionStatus"))
                        : null)
                .errorType(doc.getString("errorType"))
                .errorMessage(doc.getString("errorMessage"))
                .errorStackTrace(doc.getString("errorStackTrace"))
                .build();
    }
}
