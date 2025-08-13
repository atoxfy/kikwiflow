package io.kikwiflow.bpmn;


import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.EndEventDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.StartEventDefinitionSnapshot;
import org.junit.jupiter.api.BeforeEach;
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
    void shouldParseSimpleBpmnFileSuccessfully() throws Exception {
        //Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");
        assertNotNull(bpmnStream);

        //Act
        ProcessDefinitionSnapshot processDefinitionDeploy = defaultBpmnParser.parse(bpmnStream);


        //Assert
        assertNotNull(processDefinitionDeploy, "A definição do processo não pode ser nula.");
        assertEquals("Process_1nwhurl", processDefinitionDeploy.key(), "O ID do processo está incorreto.");
        assertEquals("Processo Teste", processDefinitionDeploy.name(), "O nome do processo está incorreto.");

        // Check nodes
        assertEquals(4, processDefinitionDeploy.flowNodes().size(), "O número de nós de fluxo está incorreto.");

        //StartEvent
        FlowNodeDefinitionSnapshot startEvent = processDefinitionDeploy.flowNodes().get("StartEvent_1");
        assertNotNull(startEvent, "O StartEvent não foi encontrado.");
        assertTrue(startEvent instanceof StartEventDefinitionSnapshot, "O nó não é do tipo StartEventNode.");
        assertEquals(1, startEvent.outgoing().size(), "O StartEvent deve ter uma saída.");
        assertEquals("Activity_0wn4t7o", startEvent.outgoing().get(0).targetNodeId(), "A saída do StartEvent aponta para o nó errado.");

        //ServiceTask 1
        FlowNodeDefinitionSnapshot task1 = processDefinitionDeploy.flowNodes().get("Activity_0wn4t7o");
        assertNotNull(task1, "A primeira ServiceTask não foi encontrada.");
        assertTrue(task1 instanceof ServiceTaskDefinitionSnapshot, "O nó não é do tipo ServiceTaskNode.");
        assertEquals("add variable", task1.name(), "O nome da primeira tarefa está incorreto.");
        assertEquals("${addVariableDelegate}", ((ServiceTaskDefinitionSnapshot) task1).delegateExpression(), "A delegate expression da primeira tarefa está incorreta.");
        assertEquals(1, task1.outgoing().size(), "A primeira tarefa deve ter uma saída.");
        assertEquals("Activity_16ovgt4", task1.outgoing().get(0).targetNodeId(), "A saída da primeira tarefa aponta para o nó errado.");

        //ServiceTask 2
        FlowNodeDefinitionSnapshot task2 = processDefinitionDeploy.flowNodes().get("Activity_16ovgt4");
        assertNotNull(task2, "A segunda ServiceTask não foi encontrada.");
        assertEquals("remove variable", task2.name(), "O nome da segunda tarefa está incorreto.");
        assertEquals("${removeVariableDelegate}", ((ServiceTaskDefinitionSnapshot) task2).delegateExpression(), "A delegate expression da segunda tarefa está incorreta.");

        //EndEvent
        FlowNodeDefinitionSnapshot endEvent = processDefinitionDeploy.flowNodes().get("Event_0w1t1d3");
        assertNotNull(endEvent, "O EndEvent não foi encontrado.");
        assertTrue(endEvent instanceof EndEventDefinitionSnapshot, "O nó não é do tipo EndEventNode.");
        assertTrue(endEvent.outgoing().isEmpty(), "O EndEvent não deve ter saídas.");
    }
}