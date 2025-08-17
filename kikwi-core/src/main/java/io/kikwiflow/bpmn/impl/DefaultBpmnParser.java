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
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.bpmn.model.task.HumanTask;
import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

public class DefaultBpmnParser implements BpmnParser {
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

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

        return ProcessDefinitionMapper.toSnapshot(processDefinitionGraphDeploy);
    }

    private FlowNode parseEvent(Element element, FlowNode node) {
        node.setId(element.getAttribute("id"));
        node.setName(element.getAttribute("name"));
        return node;
    }

    private HumanTask parseHumanTask(Element element) {
        HumanTask node = new HumanTask();
        node.setId(element.getAttribute("id"));
        node.setName(element.getAttribute("name"));
        return node;
    }

    private ServiceTask parseServiceTask(Element element) {
        ServiceTask node = new ServiceTask();
        node.setId(element.getAttribute("id"));
        node.setName(element.getAttribute("name"));
        node.setDelegateExpression(element.getAttributeNS(CAMUNDA_NS, "delegateExpression"));
        return node;
    }

    private SequenceFlow parseSequenceFlow(Element element) {
        SequenceFlow flow = new SequenceFlow();
        flow.setId(element.getAttribute("id"));
        flow.setTargetNodeId(element.getAttribute("targetRef"));
        return flow;
    }
}
