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
package io.kikwiflow.bpmn.impl;

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.mapper.ProcessDefinitionMapper;
import io.kikwiflow.bpmn.model.FlowNode;
import io.kikwiflow.bpmn.model.ProcessDefinitionGraph;
import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.bpmn.model.boundary.BoundaryEvent;
import io.kikwiflow.bpmn.model.boundary.InterruptiveTimerBoundaryEvent;
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.bpmn.model.gateway.ExclusiveGateway;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.bpmn.model.task.ManualTask;
import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.exception.NotImplementedException;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class DefaultBpmnParser implements BpmnParser {

    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    @Override
    public ProcessDefinition parse(InputStream bpmnXmlFileStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(bpmnXmlFileStream);

        Element processElement = (Element) doc.getElementsByTagName("bpmn:process").item(0);
        if (processElement == null) {
            throw new RuntimeException("Tag <bpmn:process> não encontrada no arquivo.");
        }

        ProcessDefinitionGraph processDefinitionGraphDeploy = new ProcessDefinitionGraph();
        processDefinitionGraphDeploy.setKey(processElement.getAttribute("id"));
        processDefinitionGraphDeploy.setName(processElement.getAttribute("name"));
        processDefinitionGraphDeploy.setDescription(processElement.getAttributeNS(BPMN_NS,"documentation"));

        NodeList childNodes = processElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                FlowNode flowNode = null;
                boolean isDefaultStartPoint = false;
                switch (element.getTagName()) {
                    case "bpmn:startEvent":
                        flowNode = parseEvent(element, new StartEvent());
                        isDefaultStartPoint = true;
                        break;
                    case "bpmn:endEvent":
                        flowNode = parseEvent(element, new EndEvent());
                        break;
                    case "bpmn:serviceTask":
                        flowNode = parseServiceTask(element);
                        break;
                    case "bpmn:userTask":
                        flowNode = parseHumanTask(element);
                        break;
                    case "bpmn:exclusiveGateway":
                        flowNode = parseExclusiveGateway(element);
                        break;
                    case "bpmn:boundaryEvent":{

                        InterruptiveTimerBoundaryEvent boundaryEvent = new InterruptiveTimerBoundaryEvent();
                        parseCommonFlowNodeAttributes(element, boundaryEvent);
                        boundaryEvent.setAttachedToRef(element.getAttribute("attachedToRef"));
                        NodeList timerDefs = element.getElementsByTagNameNS(BPMN_NS, "timerEventDefinition");
                        if(timerDefs.getLength() > 0){
                            Element timerDef = (Element) timerDefs.item(0);
                            NodeList durationNodes = timerDef.getElementsByTagNameNS(BPMN_NS,  "timeDuration");
                            if (durationNodes.getLength() > 0){
                                String durationText = durationNodes.item(0).getTextContent();
                                boundaryEvent.setDuration(durationText.trim());
                            }
                        }

                        flowNode = boundaryEvent;

                        break;
                    }
                }

                if (flowNode != null) {
                    if(isDefaultStartPoint) processDefinitionGraphDeploy.setDefaultStartPoint(flowNode);
                    processDefinitionGraphDeploy.addFlowNode(flowNode);
                }
            }
        }

        NodeList sequenceFlows = processElement.getElementsByTagName("bpmn:sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element flowElement = (Element) sequenceFlows.item(i);
            String sourceRef = flowElement.getAttribute("sourceRef");

            FlowNode sourceNode = processDefinitionGraphDeploy.getFlowNodes().get(sourceRef);
            if (sourceNode != null) {
                SequenceFlow sequenceFlow = parseSequenceFlow(flowElement);
                sourceNode.addOutgoing(sequenceFlow);
            }
        }

        for (FlowNode node : processDefinitionGraphDeploy.getFlowNodes().values()){
            if(node instanceof BoundaryEvent boundaryEvent){
                FlowNode fn = processDefinitionGraphDeploy.getFlowNodes().get(boundaryEvent.getAttachedToRef());
                if(fn instanceof ServiceTask st){
                    st.addBoundaryEvent(boundaryEvent);
                } else if (fn instanceof ManualTask mt) {
                    mt.addBoundaryEvent(boundaryEvent);
                }else{
                    throw new NotImplementedException("Boundary event not implemented for type");
                }
            }
        }

        return ProcessDefinitionMapper.toSnapshot(processDefinitionGraphDeploy);
    }

    private void parseCommonFlowNodeAttributes(Element element, FlowNode node) {
        node.setId(element.getAttribute("id"));
        node.setName(element.getAttribute("name"));

        String asyncBefore = element.getAttributeNS(CAMUNDA_NS, "asyncBefore");
        String asyncAfter = element.getAttributeNS(CAMUNDA_NS, "asyncAfter");

        node.setCommitBefore(Boolean.parseBoolean(asyncBefore));
        node.setCommitAfter(Boolean.parseBoolean(asyncAfter));
        node.setExtensionProperties(parseCamundaExtensionProperties(element));
    }

    private Map<String, String> parseCamundaExtensionProperties(Element parentElement) {
        Map<String, String> properties = new HashMap<>();
        NodeList extensionElementsList = parentElement.getElementsByTagNameNS(BPMN_NS, "extensionElements");
        if (extensionElementsList.getLength() == 0) {
            return properties;
        }

        Element extensionElements = (Element) extensionElementsList.item(0);
        NodeList extensionChildren = extensionElements.getChildNodes();

        for (int i = 0; i < extensionChildren.getLength(); i++) {
            Node child = extensionChildren.item(i);
            if (child instanceof Element && "properties".equals(child.getLocalName()) && CAMUNDA_NS.equals(child.getNamespaceURI())) {
                Element propertiesElement = (Element) child;
                NodeList propertyList = propertiesElement.getChildNodes();

                for (int j = 0; j < propertyList.getLength(); j++) {
                    Node propertyNode = propertyList.item(j);
                    if (propertyNode instanceof Element && "property".equals(propertyNode.getLocalName()) && CAMUNDA_NS.equals(propertyNode.getNamespaceURI())) {
                        Element propertyElement = (Element) propertyNode;
                        String name = propertyElement.getAttribute("name");
                        String value = propertyElement.getAttribute("value");
                        if (value == null || value.isEmpty()) {
                            value = propertyElement.getTextContent();
                        }

                        if (name != null && !name.isEmpty() && value != null) {
                            properties.put(name, value.trim());
                        }
                    }
                }
            }
        }
        return properties;
    }


    private FlowNode parseEvent(Element element, FlowNode node) {
        parseCommonFlowNodeAttributes(element, node);
        return node;
    }

    private ManualTask parseHumanTask(Element element) {
        ManualTask node = new ManualTask();
        parseCommonFlowNodeAttributes(element, node);
        return node;
    }

    private ServiceTask parseServiceTask(Element element) {
        ServiceTask node = new ServiceTask();
        parseCommonFlowNodeAttributes(element, node);
        node.setDelegateExpression(element.getAttributeNS(CAMUNDA_NS, "delegateExpression"));
        return node;
    }

    private SequenceFlow parseSequenceFlow(Element element) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(element.getAttribute("id"));
        flow.setTargetNodeId(element.getAttribute("targetRef"));

        NodeList conditionNodes = element.getElementsByTagName("bpmn:conditionExpression");
        if (conditionNodes.getLength() > 0) {
            Node conditionNode = conditionNodes.item(0);
            String conditionText = conditionNode.getTextContent();
            if (conditionText != null && !conditionText.trim().isEmpty()) {
                flow.setCondition(conditionText.trim());
            }
        }
        return flow;
    }

    private ExclusiveGateway parseExclusiveGateway(Element element){
        ExclusiveGateway node = new ExclusiveGateway();
        parseCommonFlowNodeAttributes(element, node);
        return node;
    }
}
