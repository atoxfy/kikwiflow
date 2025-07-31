package io.kikwiflow.bpmn;


import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.bpmn.model.FlowNode;
import io.kikwiflow.bpmn.model.ProcessDefinition;
import io.kikwiflow.bpmn.model.elements.EndEvent;
import io.kikwiflow.bpmn.model.elements.ServiceTask;
import io.kikwiflow.bpmn.model.elements.StartEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

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
        ProcessDefinition processDefinition = defaultBpmnParser.parse(bpmnStream);


        //Assert
        assertNotNull(processDefinition, "A definição do processo não pode ser nula.");
        assertEquals("Process_1nwhurl", processDefinition.getId(), "O ID do processo está incorreto.");
        assertEquals("Processo Teste", processDefinition.getName(), "O nome do processo está incorreto.");

        // Check nodes
        assertEquals(4, processDefinition.getFlowNodes().size(), "O número de nós de fluxo está incorreto.");

        //StartEvent
        FlowNode startEvent = processDefinition.getFlowNodes().get("StartEvent_1");
        assertNotNull(startEvent, "O StartEvent não foi encontrado.");
        assertTrue(startEvent instanceof StartEvent, "O nó não é do tipo StartEventNode.");
        assertEquals(1, startEvent.getOutgoing().size(), "O StartEvent deve ter uma saída.");
        assertEquals("Activity_0wn4t7o", startEvent.getOutgoing().get(0).getTargetNodeId(), "A saída do StartEvent aponta para o nó errado.");

        //ServiceTask 1
        FlowNode task1 = processDefinition.getFlowNodes().get("Activity_0wn4t7o");
        assertNotNull(task1, "A primeira ServiceTask não foi encontrada.");
        assertTrue(task1 instanceof ServiceTask, "O nó não é do tipo ServiceTaskNode.");
        assertEquals("Tarefa Teste 1", task1.getName(), "O nome da primeira tarefa está incorreto.");
        assertEquals("${fooDelegate}", ((ServiceTask) task1).getDelegateExpression(), "A delegate expression da primeira tarefa está incorreta.");
        assertEquals(1, task1.getOutgoing().size(), "A primeira tarefa deve ter uma saída.");
        assertEquals("Activity_16ovgt4", task1.getOutgoing().get(0).getTargetNodeId(), "A saída da primeira tarefa aponta para o nó errado.");

        //ServiceTask 2
        FlowNode task2 = processDefinition.getFlowNodes().get("Activity_16ovgt4");
        assertNotNull(task2, "A segunda ServiceTask não foi encontrada.");
        assertEquals("Tarefa Teste 2", task2.getName(), "O nome da segunda tarefa está incorreto.");
        assertEquals("${barDelegate}", ((ServiceTask) task2).getDelegateExpression(), "A delegate expression da segunda tarefa está incorreta.");

        //EndEvent
        FlowNode endEvent = processDefinition.getFlowNodes().get("Event_0w1t1d3");
        assertNotNull(endEvent, "O EndEvent não foi encontrado.");
        assertTrue(endEvent instanceof EndEvent, "O nó não é do tipo EndEventNode.");
        assertTrue(endEvent.getOutgoing().isEmpty(), "O EndEvent não deve ter saídas.");
    }
}