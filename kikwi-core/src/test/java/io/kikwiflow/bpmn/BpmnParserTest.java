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

package io.kikwiflow.bpmn;


import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.EndEventDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.HumanTaskDefinition;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;
import io.kikwiflow.model.bpmn.elements.StartEventDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnParserTest {

    private DefaultBpmnParser defaultBpmnParser;

    @BeforeEach
    void setUp(){
        defaultBpmnParser = new DefaultBpmnParser();
    }

    @Test
    @DisplayName("Should parse a simple BPMN file with service tasks successfully")
    void shouldParseSimpleBpmnFileSuccessfully() throws Exception {
        //Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");
        assertNotNull(bpmnStream, "BPMN file 'sample.bpmn' not found in test resources");

        //Act
        ProcessDefinition processDefinitionDeploy = defaultBpmnParser.parse(bpmnStream);


        //Assert
        assertNotNull(processDefinitionDeploy, "Process definition should not be null.");
        assertEquals("Process_1nwhurl", processDefinitionDeploy.key(), "The process key is incorrect.");
        assertEquals("Processo Teste", processDefinitionDeploy.name(), "The process name is incorrect.");

        // Check nodes
        assertEquals(4, processDefinitionDeploy.flowNodes().size(), "The number of flow nodes is incorrect.");

        //StartEvent
        FlowNodeDefinition startEvent = processDefinitionDeploy.flowNodes().get("StartEvent_1");
        assertNotNull(startEvent, "The StartEvent was not found.");
        assertTrue(startEvent instanceof StartEventDefinition, "The node should be an instance of StartEventDefinitionSnapshot.");
        assertEquals(1, startEvent.outgoing().size(), "The StartEvent should have one outgoing flow.");
        assertEquals("Activity_0wn4t7o", startEvent.outgoing().get(0).targetNodeId(), "The StartEvent's outgoing flow points to the wrong node.");

        //ServiceTask 1
        FlowNodeDefinition task1 = processDefinitionDeploy.flowNodes().get("Activity_0wn4t7o");
        assertNotNull(task1, "The first ServiceTask was not found.");
        assertTrue(task1 instanceof ServiceTaskDefinition, "The node should be an instance of ServiceTaskDefinitionSnapshot.");
        assertEquals("add variable", task1.name(), "The name of the first task is incorrect.");
        assertEquals("${addVariableDelegate}", ((ServiceTaskDefinition) task1).delegateExpression(), "The delegate expression of the first task is incorrect.");
        assertEquals(1, task1.outgoing().size(), "The first task should have one outgoing flow.");
        assertEquals("Activity_16ovgt4", task1.outgoing().get(0).targetNodeId(), "The first task's outgoing flow points to the wrong node.");

        //ServiceTask 2
        FlowNodeDefinition task2 = processDefinitionDeploy.flowNodes().get("Activity_16ovgt4");
        assertNotNull(task2, "The second ServiceTask was not found.");
        assertEquals("remove variable", task2.name(), "The name of the second task is incorrect.");
        assertEquals("${removeVariableDelegate}", ((ServiceTaskDefinition) task2).delegateExpression(), "The delegate expression of the second task is incorrect.");

        //EndEvent
        FlowNodeDefinition endEvent = processDefinitionDeploy.flowNodes().get("Event_0w1t1d3");
        assertNotNull(endEvent, "The EndEvent was not found.");
        assertTrue(endEvent instanceof EndEventDefinition, "The node should be an instance of EndEventDefinitionSnapshot.");
        assertTrue(endEvent.outgoing().isEmpty(), "The EndEvent should not have any outgoing flows.");
    }



    @Test
    @DisplayName("Should correctly parse a BPMN file with sequential human tasks")
    void shouldCorrectlyParseSequentialHumanTasksBpmn() throws Exception {
        // Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sequential-human-tasks.bpmn");
        assertNotNull(bpmnStream, "BPMN file 'sequential-human-tasks.bpmn' not found in test resources");

        // Act
        ProcessDefinition processDefinition = defaultBpmnParser.parse(bpmnStream);

        // Assert
        assertNotNull(processDefinition);

        // 1. Assert Process properties
        assertEquals("sequential-human-tasks", processDefinition.key());
        assertEquals("Sequential Human Tasks", processDefinition.name());

        // 2. Assert Flow Nodes count
        assertNotNull(processDefinition.flowNodes());
        assertEquals(7, processDefinition.flowNodes().size(), "Should parse 1 start event, 5 user tasks, and 1 end event");

        // 3. Assert Start Event
        FlowNodeDefinition startEvent = processDefinition.defaultStartPoint();
        assertNotNull(startEvent, "Default start point should be identified");
        assertEquals("Event_01chmjj", startEvent.id());
        assertEquals("Lead received", startEvent.name());
        assertTrue(startEvent instanceof StartEventDefinition, "Start point should be a StartEvent");
        assertEquals(1, startEvent.outgoing().size(), "Start event should have one outgoing flow");
        assertEquals("external-task-1", startEvent.outgoing().get(0).targetNodeId(), "Start event should flow to the first human task");

        // 4. Assert a specific Human Task and its connectivity
        FlowNodeDefinition task1 = processDefinition.flowNodes().get("external-task-1");
        assertNotNull(task1);
        assertTrue(task1 instanceof HumanTaskDefinition, "Node should be a HumanTask");
        assertEquals("External Task 1", task1.name());
        assertEquals(1, task1.outgoing().size(), "Task 1 should have one outgoing flow");
        assertEquals("external-task-2", task1.outgoing().get(0).targetNodeId(), "Task 1 should flow to Task 2");

        // 5. Assert End Event
        FlowNodeDefinition endEvent = processDefinition.flowNodes().get("Event_0zc4z3m");
        assertNotNull(endEvent);
        assertTrue(endEvent instanceof EndEventDefinition, "End node should be an EndEvent");
        assertTrue(endEvent.outgoing().isEmpty(), "End event should have no outgoing flows");
    }
}