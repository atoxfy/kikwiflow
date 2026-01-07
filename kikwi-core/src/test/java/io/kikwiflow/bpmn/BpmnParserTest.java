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
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
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
    @DisplayName("Deve fazer o parse de um arquivo BPMN simples com service tasks com sucesso")
    void shouldParseSimpleBpmnFileSuccessfully() throws Exception {
        //Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");
        assertNotNull(bpmnStream, "Arquivo BPMN 'sample.bpmn' não encontrado nos recursos de teste");

        //Act
        ProcessDefinition processDefinitionDeploy = defaultBpmnParser.parse(bpmnStream);


        //Assert
        assertNotNull(processDefinitionDeploy, "A definição do processo não deve ser nula.");
        assertEquals("Process_1nwhurl", processDefinitionDeploy.key(), "A chave do processo está incorreta.");
        assertEquals("Processo Teste", processDefinitionDeploy.name(), "O nome do processo está incorreto.");

        // Check nodes
        assertEquals(4, processDefinitionDeploy.flowNodes().size(), "O número de nós de fluxo está incorreto.");

        //StartEvent
        FlowNodeDefinition startEvent = processDefinitionDeploy.flowNodes().get("StartEvent_1");
        assertNotNull(startEvent, "O StartEvent não foi encontrado.");
        assertTrue(startEvent instanceof StartEventDefinition, "O nó deveria ser uma instância de StartEventDefinition.");
        assertEquals(1, startEvent.outgoing().size(), "O StartEvent deveria ter um fluxo de saída.");
        assertEquals("Activity_0wn4t7o", startEvent.outgoing().get(0).targetNodeId(), "O fluxo de saída do StartEvent aponta para o nó errado.");

        //ServiceTask 1
        FlowNodeDefinition task1 = processDefinitionDeploy.flowNodes().get("Activity_0wn4t7o");
        assertNotNull(task1, "A primeira ServiceTask não foi encontrada.");
        assertTrue(task1 instanceof ExecutableTaskDefinition, "O nó deveria ser uma instância de ServiceTaskDefinition.");
        assertEquals("add variable", task1.name(), "O nome da primeira tarefa está incorreto.");
        assertEquals("${addVariableDelegate}", ((ExecutableTaskDefinition) task1).delegateExpression(), "A expressão delegate da primeira tarefa está incorreta.");
        assertEquals(1, task1.outgoing().size(), "A primeira tarefa deveria ter um fluxo de saída.");
        assertEquals("Activity_16ovgt4", task1.outgoing().get(0).targetNodeId(), "O fluxo de saída da primeira tarefa aponta para o nó errado.");

        //ServiceTask 2
        FlowNodeDefinition task2 = processDefinitionDeploy.flowNodes().get("Activity_16ovgt4");
        assertNotNull(task2, "A segunda ServiceTask não foi encontrada.");
        assertEquals("remove variable", task2.name(), "O nome da segunda tarefa está incorreto.");
        assertEquals("${removeVariableDelegate}", ((ExecutableTaskDefinition) task2).delegateExpression(), "A expressão delegate da segunda tarefa está incorreta.");

        //EndEvent
        FlowNodeDefinition endEvent = processDefinitionDeploy.flowNodes().get("Event_0w1t1d3");
        assertNotNull(endEvent, "O EndEvent não foi encontrado.");
        assertTrue(endEvent instanceof EndEventDefinition, "O nó deveria ser uma instância de EndEventDefinition.");
        assertTrue(endEvent.outgoing().isEmpty(), "O EndEvent não deveria ter fluxos de saída.");
    }



    @Test
    @DisplayName("Deve fazer o parse de um arquivo BPMN com tarefas humanas sequenciais corretamente")
    void shouldCorrectlyParseSequentialHumanTasksBpmn() throws Exception {
        // Arrange
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sequential-human-tasks.bpmn");
        assertNotNull(bpmnStream, "Arquivo BPMN 'sequential-human-tasks.bpmn' não encontrado nos recursos de teste");

        // Act
        ProcessDefinition processDefinition = defaultBpmnParser.parse(bpmnStream);

        // Assert
        assertNotNull(processDefinition);

        // 1. Assert Process properties
        assertEquals("sequential-human-tasks", processDefinition.key());
        assertEquals("Sequential Human Tasks", processDefinition.name());

        // 2. Assert Flow Nodes count
        assertNotNull(processDefinition.flowNodes());
        assertEquals(7, processDefinition.flowNodes().size(), "Deveria fazer o parse de 1 evento de início, 5 tarefas de usuário e 1 evento de fim");

        // 3. Assert Start Event
        FlowNodeDefinition startEvent = processDefinition.defaultStartPoint();
        assertNotNull(startEvent, "O ponto de início padrão deveria ser identificado");
        assertEquals("Event_01chmjj", startEvent.id());
        assertEquals("Lead received", startEvent.name());
        assertTrue(startEvent instanceof StartEventDefinition, "O ponto de início deveria ser um StartEvent");
        assertEquals(1, startEvent.outgoing().size(), "O evento de início deveria ter um fluxo de saída");
        assertEquals("external-task-1", startEvent.outgoing().get(0).targetNodeId(), "O evento de início deveria fluir para a primeira tarefa humana");

        // 4. Assert a specific Human Task and its connectivity
        FlowNodeDefinition task1 = processDefinition.flowNodes().get("external-task-1");
        assertNotNull(task1);
        assertTrue(task1 instanceof ExternalTaskDefinition, "O nó deveria ser uma HumanTask");
        assertEquals("External Task 1", task1.name());
        assertEquals(1, task1.outgoing().size(), "A Tarefa 1 deveria ter um fluxo de saída");
        assertEquals("external-task-2", task1.outgoing().get(0).targetNodeId(), "A Tarefa 1 deveria fluir para a Tarefa 2");

        // 5. Assert End Event
        FlowNodeDefinition endEvent = processDefinition.flowNodes().get("Event_0zc4z3m");
        assertNotNull(endEvent);
        assertTrue(endEvent instanceof EndEventDefinition, "O nó final deveria ser um EndEvent");
        assertTrue(endEvent.outgoing().isEmpty(), "O evento de fim não deveria ter fluxos de saída");
    }
}