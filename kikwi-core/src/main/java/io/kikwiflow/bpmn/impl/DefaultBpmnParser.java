package io.kikwiflow.bpmn.impl;

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.model.FlowNode;
import io.kikwiflow.bpmn.model.ProcessDefinition;
import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.bpmn.model.elements.EndEvent;
import io.kikwiflow.bpmn.model.elements.ServiceTask;
import io.kikwiflow.bpmn.model.elements.StartEvent;
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
            throw new RuntimeException("Tag <bpmn:process> não encontrada no ficheiro.");
        }

        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setId(processElement.getAttribute("id"));
        processDefinition.setName(processElement.getAttribute("name"));

        NodeList childNodes = processElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                FlowNode flowNode = null;

                switch (element.getTagName()) {
                    case "bpmn:startEvent":
                        flowNode = parseEvent(element, new StartEvent());
                        break;
                    case "bpmn:endEvent":
                        flowNode = parseEvent(element, new EndEvent());
                        break;
                    case "bpmn:serviceTask":
                        flowNode = parseServiceTask(element);
                        break;
                }

                if (flowNode != null) {
                    processDefinition.addFlowNode(flowNode);
                }
            }
        }

        NodeList sequenceFlows = processElement.getElementsByTagName("bpmn:sequenceFlow");
        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element flowElement = (Element) sequenceFlows.item(i);
            String sourceRef = flowElement.getAttribute("sourceRef");

            FlowNode sourceNode = processDefinition.getFlowNodes().get(sourceRef);
            if (sourceNode != null) {
                SequenceFlow sequenceFlow = parseSequenceFlow(flowElement);
                sourceNode.addOutgoing(sequenceFlow);
            }
        }

        return processDefinition;
    }

    private FlowNode parseEvent(Element element, FlowNode node) {
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
