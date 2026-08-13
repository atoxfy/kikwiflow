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
package io.kikwiflow.persistence.mongodb.mapper.definition;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.JoinGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.CallActivityDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.DefaultEndEventDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.DefaultStartEventDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ErrorHandlerDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.EventCatcherDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.EventThrowerDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ExclusiveGatewayDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ExecutableTaskDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ExternalTaskDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.InterruptiveCatchEventDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.InterruptiveTimerEventDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.JoinGatewayDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.NonInterruptiveTimerEventDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.ParallelGatewayDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.nodes.TimerTaskDefinitionMapper;
import org.bson.Document;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public final class ProcessDefinitionMapper {

    private static final Map<String, Function<Document, FlowNodeDefinition>> fromDocMappers;

    static {

        // Map.of() vai só até 10 pares chave/valor — com 12 tipos de nó, precisa de Map.ofEntries(...).
        fromDocMappers = Map.ofEntries(
                Map.entry(StartEventDefinition.class.getName(), DefaultStartEventDefinitionMapper::mapToDefinition),
                Map.entry(EndEventDefinition.class.getName(), DefaultEndEventDefinitionMapper::mapToDefinition),
                Map.entry(ExecutableTaskDefinition.class.getName(), ExecutableTaskDefinitionMapper::mapToDefinition),
                Map.entry(ExternalTaskDefinition.class.getName(), ExternalTaskDefinitionMapper::mapToDefinition),
                Map.entry(ExclusiveGatewayDefinition.class.getName(), ExclusiveGatewayDefinitionMapper::mapToDefinition),
                Map.entry(JoinGatewayDefinition.class.getName(), JoinGatewayDefinitionMapper::mapToDefinition),
                Map.entry(ParallelGatewayDefinition.class.getName(), ParallelGatewayDefinitionMapper::mapToDefinition),
                Map.entry(InterruptiveTimerEventDefinition.class.getName(), InterruptiveTimerEventDefinitionMapper::mapToDefinition),
                Map.entry(NonInterruptiveTimerEventDefinition.class.getName(), NonInterruptiveTimerEventDefinitionMapper::mapToDefinition),
                Map.entry(ErrorHandlerDefinition.class.getName(), ErrorHandlerDefinitionMapper::mapToDefinition),
                Map.entry(EventCatcherDefinition.class.getName(), EventCatcherDefinitionMapper::mapToDefinition),
                Map.entry(InterruptiveCatchEventDefinition.class.getName(), InterruptiveCatchEventDefinitionMapper::mapToDefinition),
                Map.entry(TimerTaskDefinition.class.getName(), TimerTaskDefinitionMapper::mapToDefinition),
                Map.entry(EventThrowerDefinition.class.getName(), EventThrowerDefinitionMapper::mapToDefinition),
                Map.entry(CallActivityDefinition.class.getName(), CallActivityDefinitionMapper::mapToDefinition)
        );
    }

    private ProcessDefinitionMapper() {}

    public static Document toDocument(ProcessDefinition definition) {
        if (definition == null) {
            return null;
        }

        Document doc = new Document("_id", definition.id())
                .append("key", definition.key())
                .append("sla", definition.sla())
                .append("name", definition.name())
                .append("version", definition.version())
                .append("checksum", definition.checksum())
                .append("description", definition.description())
                .append("extensionProperties", definition.extensionProperties() != null ? new Document(definition.extensionProperties()) : new Document());


        if (definition.flowNodes() != null) {
            Document nodesDoc = new Document();
            definition.flowNodes().forEach((id, node) -> {
                nodesDoc.append(id, toDocument(node));
            });

            doc.append("flowNodes", nodesDoc);
        }

        if (definition.defaultStartPoint() != null) {
            doc.append("defaultStartPointId", definition.defaultStartPoint());
        }

        return doc;
    }

    private static Document toDocument(FlowNodeDefinition node) {

        if (node == null) {
            return null;
        }

        Document doc = new Document("id", node.id())
                .append("name", node.name())
                .append("description", node.description())
                .append("commitBefore", node.commitBefore())
                .append("commitAfter", node.commitAfter())
                .append("layout", node.layout() != null ? new Document().append("x", node.layout().x())
                        .append("y", node.layout().y()) : null)
                .append("extensionProperties", node.extensionProperties() != null ? new Document(node.extensionProperties()) : new Document());

        doc.append("_class", node.getClass().getName());

        if (node.outgoing() != null) {
            doc.append("outgoing", node.outgoing().stream()
                    .map(ProcessDefinitionMapper::toDocument)
                    .collect(Collectors.toList()));
        }

        switch (node) {
            case ExecutableTaskDefinition st -> {
                ExecutableTaskDefinitionMapper.toDocument(doc, st);
            }
            case ExclusiveGatewayDefinition gt-> {
               ExclusiveGatewayDefinitionMapper.mapToDoc(doc, gt);
            }
            case ParallelGatewayDefinition gt-> {
                ParallelGatewayDefinitionMapper.mapToDocument(doc, gt);
            }
            case JoinGatewayDefinition gt -> {
                JoinGatewayDefinitionMapper.mapToDocument(doc, gt);
            }
            case ExternalTaskDefinition mt -> {
                ExternalTaskDefinitionMapper.toDocument(doc, mt);
            }
            case InterruptiveTimerEventDefinition te -> {
                InterruptiveTimerEventDefinitionMapper.mapToDocument(doc, te);
            }
            case NonInterruptiveTimerEventDefinition te -> {
                NonInterruptiveTimerEventDefinitionMapper.mapToDocument(doc, te);
            }
            case ErrorHandlerDefinition te -> {
                ErrorHandlerDefinitionMapper.mapToDocument(doc, te);
            }
            case EventCatcherDefinition ec -> {
                EventCatcherDefinitionMapper.mapToDocument(doc, ec);
            }
            case InterruptiveCatchEventDefinition ice -> {
                InterruptiveCatchEventDefinitionMapper.mapToDocument(doc, ice);
            }
            case TimerTaskDefinition tt -> {
                TimerTaskDefinitionMapper.mapToDocument(doc, tt);
            }
            case EventThrowerDefinition et -> {
                EventThrowerDefinitionMapper.mapToDocument(doc, et);
            }
            case CallActivityDefinition ca -> {
                CallActivityDefinitionMapper.mapToDocument(doc, ca);
            }

            default -> {
            }
        }
        return doc;
    }

    private static Document toDocument(SequenceFlowDefinition flow) {
        if (flow == null) {
            return null;
        }

        Document sequenceFlow = new Document("id", flow.id())
                .append("name", flow.name())
                .append("description", flow.description())
                .append("targetNodeId", flow.targetNodeId())
                .append("expectedAnswer", flow.expectedAnswer())
                .append("isDefault", flow.isDefault())
                .append("handlesNull", flow.handlesNull());

        if (flow.positionHandlers() != null) {
            sequenceFlow.append("positionHandlers", flow.positionHandlers()
                    .stream().map(ph ->
                        new Document("x", ph.x()).append("y", ph.y())
                    ).collect(Collectors.toList()));
        }

        return sequenceFlow;

    }

    public static ProcessDefinition fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }

        Document nodesDoc = doc.get("flowNodes", Document.class);
        Map<String, FlowNodeDefinition> flowNodes = Collections.emptyMap();
        if (nodesDoc != null) {
            flowNodes = nodesDoc.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> fromDocumentToFlowNode((Document) entry.getValue())
                    ));
        }

        String defaultStartPointId = doc.getString("defaultStartPointId");

        Document extensionPropertiesDoc = doc.get("extensionProperties", Document.class);
        Map<String, String> extensionProperties = Collections.emptyMap();
        if (extensionPropertiesDoc != null) {
            extensionProperties = extensionPropertiesDoc.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (String) entry.getValue()));
        }

        return ProcessDefinition.builder()
                .id(doc.getString("_id"))
                .key(doc.getString("key"))
                .checksum(doc.getString("checksum"))
                .name(doc.getString("name"))
                .version(doc.getInteger("version"))
                .description(doc.getString("description"))
                .flowNodes(flowNodes)
                .defaultStartPoint(defaultStartPointId)
                .extensionProperties(extensionProperties)
                .build();
    }

    private static FlowNodeDefinition fromDocumentToFlowNode(Document nodeDoc) {
        if (nodeDoc == null) {
            return null;
        }

        String className = nodeDoc.getString("_class");
        if (className == null) {
            throw new IllegalArgumentException("Documento FlowNode sem o campo '_class' para determinar o tipo.");
        }

        Function<Document, FlowNodeDefinition> mapper = fromDocMappers.get(className);
        if (mapper == null) {
            throw new IllegalArgumentException("Tipo de FlowNode desconhecido: " + className);
        }

        return mapper.apply(nodeDoc);
    }
}
