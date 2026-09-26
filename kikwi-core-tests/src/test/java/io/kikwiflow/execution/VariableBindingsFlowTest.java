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
package io.kikwiflow.execution;

import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.factory.TestEngine;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.form.FormDescriptor;
import io.kikwiflow.model.execution.form.FormField;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.variable.FormDescriptorFactory;
import io.kikwiflow.variable.VariableValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documenta o contrato de dados por nó (docs/engine/26): o catálogo {@code variableDeclarations} do processo e os
 * {@code variableBindings.inputs} do start (exigidos para iniciar) e de uma {@code EXTERNAL_TASK} (exigidos para
 * concluí-la), além do {@link FormDescriptor} server-driven derivado deles.
 */
@DisplayName("Dado um processo com catálogo de variáveis e inputs vinculados ao start e a uma tarefa externa")
class VariableBindingsFlowTest {

    private static final IdentityContext ACTOR = new IdentityContext("test-actor", null);

    private TestEngine testEngine;
    private ProcessDefinition definition;

    @BeforeEach
    void setUp() {
        testEngine = SingletonsFactory.engine().build();
        definition = testEngine.deploy("/processes/variable-bindings.json");
    }

    private static Map<String, ProcessVariable> vars(Object... keyValues) {
        Map<String, ProcessVariable> variables = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            variables.put(key, new ProcessVariable(key, keyValues[i + 1]));
        }
        return variables;
    }

    private static Map<String, String> codesByVariable(VariableValidationException ex) {
        return ex.getErrors().stream()
                .collect(Collectors.toMap(VariableValidationError::variable, VariableValidationError::code));
    }

    private ProcessInstance startValid(String businessKey) {
        return testEngine.engine().startProcess()
                .byKey(definition.key())
                .withBusinessKey(businessKey)
                .withVariables(vars("customerName", "Maria", "segment", "PF"))
                .execute();
    }

    private String reviewTaskId(ProcessInstance instance) {
        List<ExternalTask> tasks = testEngine.repository().findExternalTasksByProcessInstanceId(instance.id());
        assertEquals(1, tasks.size());
        return tasks.get(0).id();
    }

    @Test
    @DisplayName("Quando o .kikwi é implantado, então catálogo e vínculos são deserializados do JSON")
    void deploysCatalogAndBindingsFromJson() {
        assertEquals(7, definition.variableDeclarations().size());
        assertEquals(VariableFormat.MONEY, definition.findVariableDeclaration("amount").orElseThrow().format());
        assertEquals("BRL", definition.findVariableDeclaration("amount").orElseThrow().currency());
        assertEquals(2, definition.findVariableDeclaration("segment").orElseThrow().options().size());
    }

    @Nested
    @DisplayName("Quando o processo é iniciado")
    class Start {

        @Test
        @DisplayName("sem as variáveis obrigatórias, então falha com REQUIRED por campo e nada é persistido")
        void rejectsMissingRequiredInputs() {
            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().startProcess()
                            .byKey(definition.key())
                            .withBusinessKey("BK-VAR-1")
                            .execute());

            assertEquals("START_EVENT", ex.getNodeId());
            assertEquals(Map.of("customerName", VariableValidationError.REQUIRED,
                    "segment", VariableValidationError.REQUIRED), codesByVariable(ex));
            assertEquals(0, testEngine.repository().countProcessInstancesByProcessDefinition(definition.id()));
        }

        @Test
        @DisplayName("com um valor fora das opções de um SINGLE_SELECT, então falha com INVALID_OPTION")
        void rejectsValueOutsideSelectOptions() {
            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().startProcess()
                            .byKey(definition.key())
                            .withBusinessKey("BK-VAR-2")
                            .withVariables(vars("customerName", "Maria", "segment", "GOV"))
                            .execute());

            assertEquals(Map.of("segment", VariableValidationError.INVALID_OPTION), codesByVariable(ex));
        }

        @Test
        @DisplayName("com um campo opcional preenchido fora do regex, então falha com a mensagem declarada")
        void rejectsOptionalFieldNotMatchingRegex() {
            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().startProcess()
                            .byKey(definition.key())
                            .withBusinessKey("BK-VAR-3")
                            .withVariables(vars("customerName", "Maria", "segment", "PF", "email", "not-an-email"))
                            .execute());

            assertEquals(1, ex.getErrors().size());
            assertEquals(VariableValidationError.PATTERN_MISMATCH, ex.getErrors().get(0).code());
            assertEquals("Informe um e-mail válido.", ex.getErrors().get(0).message());
        }

        @Test
        @DisplayName("com os inputs válidos, então a instância fica ativa aguardando a tarefa externa")
        void startsWhenInputsAreValid() {
            ProcessInstance instance = startValid("BK-VAR-4");

            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "REVIEW");
        }
    }

    @Nested
    @DisplayName("Quando a tarefa externa é concluída")
    class Complete {

        @Test
        @DisplayName("sem os inputs obrigatórios, então falha e a tarefa continua pendente")
        void rejectsMissingRequiredInputsAndKeepsTaskPending() {
            ProcessInstance instance = startValid("BK-VAR-5");
            String taskId = reviewTaskId(instance);

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(taskId, vars("notes", "sem valor"), ACTOR));

            // customerName veio do start e continua satisfeito: a validação usa o estado mesclado da instância.
            assertEquals(Map.of("amount", VariableValidationError.REQUIRED,
                    "approved", VariableValidationError.REQUIRED), codesByVariable(ex));
            testEngine.repository().assertThatProcessInstanceIsActive(instance.id());
            testEngine.repository().assertHasActiveExternalTaskOn(instance.id(), "REVIEW");
        }

        @Test
        @DisplayName("com formatos inválidos, então falha com INVALID_FORMAT/INVALID_OPTION")
        void rejectsInvalidFormats() {
            ProcessInstance instance = startValid("BK-VAR-6");
            String taskId = reviewTaskId(instance);

            VariableValidationException ex = assertThrows(VariableValidationException.class, () ->
                    testEngine.engine().completeExternalTask(taskId,
                            vars("amount", "mil reais", "approved", "sim", "tags", List.of("VIP", "GOLD")), ACTOR));

            assertEquals(Map.of("amount", VariableValidationError.INVALID_FORMAT,
                    "approved", VariableValidationError.INVALID_FORMAT,
                    "tags", VariableValidationError.INVALID_OPTION), codesByVariable(ex));
        }

        @Test
        @DisplayName("com os inputs válidos (valores como chegam de JSON), então o processo conclui")
        void completesWhenInputsAreValid() {
            ProcessInstance instance = startValid("BK-VAR-7");
            String taskId = reviewTaskId(instance);

            ProcessInstance completed = testEngine.engine().completeExternalTask(taskId,
                    vars("amount", 1500.50, "approved", true, "tags", List.of("VIP")), ACTOR);

            assertEquals(ProcessInstanceStatus.COMPLETED, completed.status());
        }
    }

    @Nested
    @DisplayName("Quando o formulário server-driven é montado")
    class Form {

        @Test
        @DisplayName("para o start, então lista os inputs do start na ordem declarada, sem valores")
        void describesStartForm() {
            FormDescriptor form = FormDescriptorFactory.forStart(definition);

            assertEquals("START_EVENT", form.nodeId());
            assertEquals(List.of("customerName", "segment", "email"),
                    form.fields().stream().map(FormField::key).toList());
            assertTrue(form.fields().get(0).required());
            assertFalse(form.fields().get(2).required());
            assertEquals(2, form.fields().get(1).options().size());
            assertNull(form.fields().get(0).value());
        }

        @Test
        @DisplayName("para a tarefa externa, então pré-preenche os valores atuais da instância")
        void describesExternalTaskFormWithCurrentValues() {
            ProcessInstance instance = startValid("BK-VAR-8");
            ProcessInstance current = testEngine.repository().findProcessInstanceById(instance.id()).orElseThrow();

            FormDescriptor form = FormDescriptorFactory.forNode(definition, "REVIEW",
                    definition.flowNodes().get("REVIEW"), current.variables());

            FormField customerName = form.fields().get(0);
            assertEquals("customerName", customerName.key());
            assertEquals("Maria", customerName.value());
            FormField amount = form.fields().get(1);
            assertEquals(VariableFormat.MONEY, amount.format());
            assertEquals("BRL", amount.currency());
            assertEquals("0,00", amount.extensionProperties().get("placeholder"));
            assertNull(amount.value());
        }
    }
}
