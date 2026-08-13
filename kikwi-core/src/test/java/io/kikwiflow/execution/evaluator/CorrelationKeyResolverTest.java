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

package io.kikwiflow.execution.evaluator;

import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.api.dto.CorrelationItem;
import io.kikwiflow.execution.api.resolver.CorrelationKeysProviderResolver;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateSegment;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.enumerated.TemplateSegmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CorrelationKeyResolver")
class CorrelationKeyResolverTest {

    private CorrelationKeysProviderResolver providerResolver;
    private CorrelationKeyResolver resolver;

    @BeforeEach
    void setUp() {
        providerResolver = name -> Optional.empty();
        resolver = new CorrelationKeyResolver(providerResolver);
    }

    private ProcessInstanceExecution executionWith(Map<String, Object> variables) {
        ProcessInstanceExecution execution = new ProcessInstanceExecution();
        execution.setId("proc-1");
        Map<String, ProcessVariable> vars = new HashMap<>();
        variables.forEach((k, v) -> vars.put(k, new ProcessVariable(k, v)));
        execution.setVariables(vars);
        return execution;
    }

    @Nested
    @DisplayName("providerType STATIC")
    class Static {

        @Test
        @DisplayName("resolve a chave e o alias fixos configurados na definição")
        void resolvesFixedKey() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.STATIC)
                    .staticKey("FIXED_KEY")
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of()));

            assertEquals(1, items.size());
            assertEquals("FIXED_KEY", items.get(0).key());
            assertEquals("FIXED_KEY", items.get(0).displayName());
        }
    }

    @Nested
    @DisplayName("providerType VARIABLE")
    class Variable {

        @Test
        @DisplayName("STANDALONE lê uma variável simples e aplica keyPrefix/keySuffix")
        void resolvesSingleVariableWithPrefixSuffix() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("orderId")
                    .keyPrefix("ORDER_")
                    .keySuffix("_PAID")
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("orderId", "9988")));

            assertEquals(1, items.size());
            assertEquals("ORDER_9988_PAID", items.get(0).key());
        }

        @Test
        @DisplayName("GROUP lê uma lista e gera um item por elemento")
        void resolvesListIntoMultipleItems() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("productIds")
                    .keyPrefix("PROD_")
                    .keySuffix("_ACTIVATED")
                    .matchPolicy(MatchPolicy.ALL)
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("productIds", List.of("A", "B"))));

            assertEquals(2, items.size());
            assertEquals("PROD_A_ACTIVATED", items.get(0).key());
            assertEquals("PROD_B_ACTIVATED", items.get(1).key());
        }

        @Test
        @DisplayName("GROUP com variável duplicada dedup preservando a ordem")
        void dedupsRepeatedKeysPreservingOrder() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("productIds")
                    .matchPolicy(MatchPolicy.ALL)
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("productIds", List.of("A", "B", "A"))));

            assertEquals(2, items.size());
            assertEquals("A", items.get(0).key());
            assertEquals("B", items.get(1).key());
        }

        @Test
        @DisplayName("GROUP com variável que não é lista lança IllegalStateException")
        void nonListVariableInGroupModeThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("productIds")
                    .matchPolicy(MatchPolicy.ALL)
                    .build();

            assertThrows(IllegalStateException.class, () ->
                    resolver.resolve(def, executionWith(Map.of("productIds", "not-a-list"))));
        }

        @Test
        @DisplayName("variável ausente lança IllegalStateException")
        void missingVariableThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("orderId")
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }
    }

    @Nested
    @DisplayName("providerType BEAN")
    class Bean {

        @Test
        @DisplayName("delega a resolução para o CorrelationKeysProvider registrado")
        void delegatesToRegisteredProvider() {
            providerResolver = name -> "productCorrelationResolver".equals(name)
                    ? Optional.of(ctx -> List.of(new CorrelationItem("PROD_1", "Produto 1"), new CorrelationItem("PROD_2", "Produto 2")))
                    : Optional.empty();
            resolver = new CorrelationKeyResolver(providerResolver);

            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.BEAN)
                    .providerBean("productCorrelationResolver")
                    .matchPolicy(MatchPolicy.ANY)
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of()));

            assertEquals(2, items.size());
            assertEquals("Produto 1", items.get(0).displayName());
        }

        @Test
        @DisplayName("bean não encontrado lança IllegalStateException")
        void unknownBeanThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.BEAN)
                    .providerBean("doesNotExist")
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }
    }

    @Nested
    @DisplayName("providerType TEMPLATE")
    class Template {

        @Test
        @DisplayName("STANDALONE encadeia literal + variável + literal numa única chave")
        void resolvesSingleTemplateChainingLiteralAndVariable() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.TEMPLATE)
                    .correlationTemplates(List.of(new CorrelationTemplateDefinition(
                            List.of(
                                    new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "CONTA_CORRENTE_"),
                                    new CorrelationTemplateSegment(TemplateSegmentType.VARIABLE, "cpf"),
                                    new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "_EFETIVADA")),
                            List.of(new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "Efetivação da Conta Corrente")))))
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("cpf", "12345678900")));

            assertEquals(1, items.size());
            assertEquals("CONTA_CORRENTE_12345678900_EFETIVADA", items.get(0).key());
            assertEquals("Efetivação da Conta Corrente", items.get(0).displayName());
        }

        @Test
        @DisplayName("GROUP declara uma lista fixa de templates, um por evento esperado")
        void resolvesFixedListOfTemplatesInGroupMode() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.TEMPLATE)
                    .matchPolicy(MatchPolicy.ALL)
                    .correlationTemplates(List.of(
                            new CorrelationTemplateDefinition(
                                    List.of(new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "CONTA_CORRENTE_"),
                                            new CorrelationTemplateSegment(TemplateSegmentType.VARIABLE, "cpf"),
                                            new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "_EFETIVADA")),
                                    null),
                            new CorrelationTemplateDefinition(
                                    List.of(new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "CARTAO_CREDITO_"),
                                            new CorrelationTemplateSegment(TemplateSegmentType.VARIABLE, "cpf"),
                                            new CorrelationTemplateSegment(TemplateSegmentType.LITERAL, "_EFETIVADA")),
                                    null)))
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("cpf", "999")));

            assertEquals(2, items.size());
            assertEquals("CONTA_CORRENTE_999_EFETIVADA", items.get(0).key());
            assertEquals("CARTAO_CREDITO_999_EFETIVADA", items.get(1).key());
            // sem displayNameSegments -> displayName cai de volta para a própria chave
            assertEquals("CONTA_CORRENTE_999_EFETIVADA", items.get(0).displayName());
        }

        @Test
        @DisplayName("correlationTemplates vazio lança IllegalStateException")
        void emptyTemplatesThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.TEMPLATE)
                    .correlationTemplates(List.of())
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }

        @Test
        @DisplayName("segmento VARIABLE referenciando variável inexistente lança IllegalStateException")
        void missingVariableInSegmentThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.TEMPLATE)
                    .correlationTemplates(List.of(new CorrelationTemplateDefinition(
                            List.of(new CorrelationTemplateSegment(TemplateSegmentType.VARIABLE, "doesNotExist")),
                            null)))
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }
    }

    @Nested
    @DisplayName("validações de forma")
    class Shape {

        @Test
        @DisplayName("lista vazia lança IllegalStateException")
        void emptyListThrows() {
            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.GROUP)
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("productIds")
                    .matchPolicy(MatchPolicy.ALL)
                    .build();

            assertThrows(IllegalStateException.class, () ->
                    resolver.resolve(def, executionWith(Map.of("productIds", List.<String>of()))));
        }

        @Test
        @DisplayName("STANDALONE resolvendo mais de 1 item lança IllegalStateException")
        void standaloneResolvingMultipleItemsThrows() {
            providerResolver = name -> Optional.of(ctx -> List.of(new CorrelationItem("A"), new CorrelationItem("B")));
            resolver = new CorrelationKeyResolver(providerResolver);

            EventCatcherDefinition def = EventCatcherDefinition.builder()
                    .id("WAIT")
                    .catchType(CatchType.STANDALONE)
                    .providerType(CorrelationProviderType.BEAN)
                    .providerBean("someBean")
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }
    }

    @Nested
    @DisplayName("InterruptiveCatchEventDefinition (evento de borda, sempre 1 chave)")
    class BoundaryCatchEvent {

        @Test
        @DisplayName("providerType STATIC resolve a chave fixa configurada na definição")
        void resolvesStaticKey() {
            InterruptiveCatchEventDefinition def = InterruptiveCatchEventDefinition.builder()
                    .id("CANCEL_CATCH")
                    .attachedToRef("COLETAR_DADOS")
                    .providerType(CorrelationProviderType.STATIC)
                    .staticKey("cancelar-task-15649234")
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of()));

            assertEquals(1, items.size());
            assertEquals("cancelar-task-15649234", items.get(0).key());
        }

        @Test
        @DisplayName("providerType VARIABLE lê uma variável simples e aplica keyPrefix")
        void resolvesVariableWithPrefix() {
            InterruptiveCatchEventDefinition def = InterruptiveCatchEventDefinition.builder()
                    .id("CANCEL_CATCH")
                    .attachedToRef("COLETAR_DADOS")
                    .providerType(CorrelationProviderType.VARIABLE)
                    .providerVariable("taskId")
                    .keyPrefix("CANCELAR_TASK_")
                    .build();

            List<CorrelationItem> items = resolver.resolve(def, executionWith(Map.of("taskId", "15649234")));

            assertEquals(1, items.size());
            assertEquals("CANCELAR_TASK_15649234", items.get(0).key());
        }

        @Test
        @DisplayName("BEAN resolvendo mais de 1 item lança IllegalStateException (evento de borda nunca é GROUP)")
        void beanResolvingMultipleItemsThrows() {
            providerResolver = name -> Optional.of(ctx -> List.of(new CorrelationItem("A"), new CorrelationItem("B")));
            resolver = new CorrelationKeyResolver(providerResolver);

            InterruptiveCatchEventDefinition def = InterruptiveCatchEventDefinition.builder()
                    .id("CANCEL_CATCH")
                    .attachedToRef("COLETAR_DADOS")
                    .providerType(CorrelationProviderType.BEAN)
                    .providerBean("someBean")
                    .build();

            assertThrows(IllegalStateException.class, () -> resolver.resolve(def, executionWith(Map.of())));
        }
    }
}
