/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.*;
import org.bson.Document;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public final class ProcessDefinitionMapper {

    private static final Map<String, Function<Document, FlowNodeDefinition>> fromDocMappers;

    static {
        fromDocMappers = Map.of(
                StartEventDefinition.class.getName(), ProcessDefinitionMapper::fromDocToStartEvent,
                EndEventDefinition.class.getName(), ProcessDefinitionMapper::fromDocToEndEvent,
                ServiceTaskDefinition.class.getName(), ProcessDefinitionMapper::fromDocToServiceTask,
                ManualTaskDefinition.class.getName(), ProcessDefinitionMapper::fromDocToManualTask,
                ExclusiveGatewayDefinition.class.getName(), ProcessDefinitionMapper::fromDocToExclusiveGateway,
                InterruptiveTimerEventDefinition.class.getName(), ProcessDefinitionMapper::fromDocToInterruptiveTimerEvent
        );
    }

    private ProcessDefinitionMapper() {
    }

    public static Document toDocument(ProcessDefinition definition) {
        if (definition == null) {
            return null;
        }

        Document doc = new Document("_id", definition.id())
                .append("key", definition.key())
                .append("name", definition.name())
                .append("version", definition.version())
                .append("description", definition.description());

        if (definition.flowNodes() != null) {
            Document nodesDoc = new Document();
            definition.flowNodes().forEach((id, node) -> {
                nodesDoc.append(id, toDocument(node));
            });
            doc.append("flowNodes", nodesDoc);
        }

        if (definition.defaultStartPoint() != null) {
            doc.append("defaultStartPointId", definition.defaultStartPoint().id());
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
                .append("extensionProperties", node.extensionProperties() != null ? new Document(node.extensionProperties()) : new Document());

        doc.append("_class", node.getClass().getName());

        if (node.outgoing() != null) {
            doc.append("outgoing", node.outgoing().stream()
                    .map(ProcessDefinitionMapper::toDocument)
                    .collect(Collectors.toList()));
        }

        switch (node) {
            case ServiceTaskDefinition st -> {
                doc.append("delegateExpression", st.delegateExpression());
                if (st.boundaryEvents() != null) {
                    doc.append("boundaryEvents", st.boundaryEvents().stream()
                            .map(ProcessDefinitionMapper::toDocument)
                            .collect(Collectors.toList()));
                }
            }
            case ManualTaskDefinition mt -> {
                if (mt.boundaryEvents() != null) {
                    doc.append("boundaryEvents", mt.boundaryEvents().stream()
                            .map(ProcessDefinitionMapper::toDocument)
                            .collect(Collectors.toList()));
                }
            }
            case BoundaryEventDefinition be -> {
                doc.append("attachedToRef", be.attachedToRef());
                if (be instanceof InterruptiveTimerEventDefinition te) {
                    doc.append("duration", te.duration());
                }
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
        return new Document("id", flow.id())
                .append("targetNodeId", flow.targetNodeId())
                .append("condition", flow.condition());
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
        FlowNodeDefinition defaultStartPoint = defaultStartPointId != null ? flowNodes.get(defaultStartPointId) : null;

        return ProcessDefinition.builder()
                .id(doc.getString("_id"))
                .key(doc.getString("key"))
                .name(doc.getString("name"))
                .version(doc.getInteger("version"))
                .description(doc.getString("description"))
                .flowNodes(flowNodes)
                .defaultStartPoint(defaultStartPoint)
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

    private static StartEventDefinition fromDocToStartEvent(Document doc) {
        return StartEventDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .build();
    }

    private static EndEventDefinition fromDocToEndEvent(Document doc) {
        return EndEventDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .build();
    }

    private static ServiceTaskDefinition fromDocToServiceTask(Document doc) {
        return ServiceTaskDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .delegateExpression(doc.getString("delegateExpression"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .boundaryEvents(fromDocToBoundaryEventsList(doc))
                .build();
    }

    private static ManualTaskDefinition fromDocToManualTask(Document doc) {
        return ManualTaskDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .boundaryEvents(fromDocToBoundaryEventsList(doc))
                .build();
    }

    private static ExclusiveGatewayDefinition fromDocToExclusiveGateway(Document doc) {
        return ExclusiveGatewayDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .build();
    }

    private static InterruptiveTimerEventDefinition fromDocToInterruptiveTimerEvent(Document doc) {
        return InterruptiveTimerEventDefinition.builder()
                .id(doc.getString("id"))
                .name(doc.getString("name"))
                .description(doc.getString("description"))
                .commitBefore(doc.getBoolean("commitBefore"))
                .commitAfter(doc.getBoolean("commitAfter"))
                .attachedToRef(doc.getString("attachedToRef"))
                .duration(doc.getString("duration"))
                .extensionProperties(fromDocToExtensionProperties(doc.get("extensionProperties", Document.class)))
                .outgoing(fromDocToOutgoingList(doc))
                .build();
    }


    private static List<SequenceFlowDefinition> fromDocToOutgoingList(Document doc) {
        List<Document> outgoingDocs = doc.getList("outgoing", Document.class, Collections.emptyList());
        return outgoingDocs.stream()
                .map(ProcessDefinitionMapper::fromDocToSequenceFlow)
                .collect(Collectors.toList());
    }

    private static List<BoundaryEventDefinition> fromDocToBoundaryEventsList(Document doc) {
        List<Document> boundaryDocs = doc.getList("boundaryEvents", Document.class, Collections.emptyList());
        return boundaryDocs.stream()
                .map(d -> (BoundaryEventDefinition) fromDocumentToFlowNode(d)) // Reutiliza o mapper principal
                .collect(Collectors.toList());
    }

    private static SequenceFlowDefinition fromDocToSequenceFlow(Document flowDoc) {
        if (flowDoc == null) return null;
        return new SequenceFlowDefinition(
                flowDoc.getString("id"),
                flowDoc.getString("condition"),
                flowDoc.getString("targetNodeId")
        );
    }
    
    private static Map<String, String> fromDocToExtensionProperties(Document document){
        if (document == null) {
            return Collections.emptyMap();
        }
        Map<String, String> properties = new HashMap<String, String>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (entry.getValue() != null) {
                properties.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return properties;
    }
}
