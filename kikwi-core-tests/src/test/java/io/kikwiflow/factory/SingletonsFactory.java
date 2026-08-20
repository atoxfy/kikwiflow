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

package io.kikwiflow.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.assertion.AssertableKikwiEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.execution.ContinuationService;
import io.kikwiflow.execution.EventThrowExecutor;
import io.kikwiflow.execution.FailureHandler;
import io.kikwiflow.execution.FlowNodeExecutor;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.event.CriticalEventRecorder;
import io.kikwiflow.execution.TaskAcquirer;
import io.kikwiflow.execution.TaskExecutor;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.execution.api.parser.ProcessDefinitionParser;
import io.kikwiflow.execution.api.provider.AnswerProvider;
import io.kikwiflow.execution.api.provider.CorrelationKeysProvider;
import io.kikwiflow.execution.api.provider.DueDateProvider;
import io.kikwiflow.execution.evaluator.CorrelationKeyResolver;
import io.kikwiflow.execution.evaluator.TimerDueDateEvaluator;
import io.kikwiflow.execution.policy.DefaultRetryPolicyEvaluator;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.parser.jackson.JacksonProcessDefinitionParser;
import io.kikwiflow.parser.jackson.KikwiflowJacksonModule;
import io.kikwiflow.security.DefaultDeploymentSecurityManager;
import io.kikwiflow.validation.DeployValidator;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Monta um {@link KikwiflowEngine} de teste, replicando manualmente a mesma árvore de construção que
 * {@code KikwiflowAutoConfiguration} monta via Spring em produção — sem Spring, sem MongoDB: o repositório é
 * {@link AssertableKikwiEngine} (em memória), e os resolvers de {@link TaskHandler}/{@link AnswerProvider}/
 * {@link DueDateProvider} são simples mapas registrados pelo próprio teste.
 *
 * <p>O {@link TaskAcquirer} interno é construído mas nunca iniciado — testes dirigem execução assíncrona
 * manualmente (via {@code engine.executeFromTask(...)}/{@code engine.completeExternalTask(...)}) em vez de
 * depender do poller em background, o que mantém os testes determinísticos.
 *
 * <pre>{@code
 * TestEngine testEngine = SingletonsFactory.engine()
 *         .withTaskHandler("calculateRisk", ctx -> { ... })
 *         .withAnswerProvider("riskStrategy", ctx -> "APROVADO")
 *         .build();
 *
 * ProcessDefinition definition = testEngine.deploy("/processes/executable-task-flow.json");
 * ProcessInstance instance = testEngine.engine().startProcess()
 *         .byKey(definition.key())
 *         .withBusinessKey("BK-1")
 *         .execute();
 *
 * testEngine.repository().assertThatProcessInstanceIsCompleted(instance.id());
 * }</pre>
 */
public class SingletonsFactory {

    private SingletonsFactory() {}

    public static EngineHarness engine() {
        return new EngineHarness();
    }

    public static class EngineHarness {
        private final AssertableKikwiEngine assertableKikwiEngine = new AssertableKikwiEngine();
        private final MapTaskHandlerResolver taskHandlerResolver = new MapTaskHandlerResolver();
        private final MapAnswerProviderResolver answerProviderResolver = new MapAnswerProviderResolver();
        private final MapDueDateProviderResolver dueDateProviderResolver = new MapDueDateProviderResolver();
        private final MapCorrelationKeysProviderResolver correlationKeysProviderResolver = new MapCorrelationKeysProviderResolver();
        private final KikwiflowConfig config = new KikwiflowConfig();

        private EngineHarness() {}

        public EngineHarness withTaskHandler(String beanName, TaskHandler handler) {
            taskHandlerResolver.register(beanName, handler);
            return this;
        }

        public EngineHarness withAnswerProvider(String beanName, AnswerProvider provider) {
            answerProviderResolver.register(beanName, provider);
            return this;
        }

        public EngineHarness withDueDateProvider(String beanName, DueDateProvider provider) {
            dueDateProviderResolver.register(beanName, provider);
            return this;
        }

        public EngineHarness withCorrelationKeysProvider(String beanName, CorrelationKeysProvider provider) {
            correlationKeysProviderResolver.register(beanName, provider);
            return this;
        }

        public EngineHarness withConfig(Consumer<KikwiflowConfig> customizer) {
            customizer.accept(config);
            return this;
        }

        public TestEngine build() {
            DefaultDeploymentSecurityManager deploymentSecurityManager = new DefaultDeploymentSecurityManager(true);
            DeployValidator deployValidator = new DeployValidator(taskHandlerResolver, answerProviderResolver, correlationKeysProviderResolver);

            // FAIL_ON_UNKNOWN_PROPERTIES desligado para casar com o ObjectMapper auto-configurado do Spring Boot
            // em produção (que desliga essa feature por padrão) — fixtures .kikwi reais trazem campos que o
            // editor gráfico usa (ex.: "transitionType", "positionHandlers") sem correspondência no modelo Java.
            ObjectMapper objectMapper = new ObjectMapper()
                    .registerModule(new KikwiflowJacksonModule())
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            ProcessDefinitionParser parser = new JacksonProcessDefinitionParser(objectMapper);

            ProcessDefinitionService processDefinitionService = new ProcessDefinitionService(
                    parser, assertableKikwiEngine, deployValidator, deploymentSecurityManager);

            Navigator navigator = new Navigator(answerProviderResolver);
            CriticalEventRecorder criticalEventRecorder = new CriticalEventRecorder(config);
            CorrelationKeyResolver correlationKeyResolver = new CorrelationKeyResolver(correlationKeysProviderResolver);
            EventThrowExecutor eventThrowExecutor = new EventThrowExecutor(correlationKeyResolver);
            ProcessExecutionManager processExecutionManager = new ProcessExecutionManager(
                    new FlowNodeExecutor(new TaskExecutor(taskHandlerResolver), eventThrowExecutor), navigator, criticalEventRecorder);

            TimerDueDateEvaluator timerDueDateEvaluator = new TimerDueDateEvaluator(dueDateProviderResolver);
            ContinuationService continuationService = new ContinuationService(assertableKikwiEngine, timerDueDateEvaluator, correlationKeyResolver, config, criticalEventRecorder);

            DefaultRetryPolicyEvaluator retryPolicyEvaluator = new DefaultRetryPolicyEvaluator(config);
            FailureHandler failureHandler = new FailureHandler(assertableKikwiEngine, retryPolicyEvaluator, criticalEventRecorder);

            AsynchronousEventPublisher eventPublisher = new AsynchronousEventPublisher(Executors.newVirtualThreadPerTaskExecutor());
            TaskAcquirer taskAcquirer = new TaskAcquirer(assertableKikwiEngine, config);

            KikwiflowEngine kikwiflowEngine = new KikwiflowEngine(
                    processDefinitionService, navigator, processExecutionManager, assertableKikwiEngine,
                    config, eventPublisher, continuationService, failureHandler, taskAcquirer, criticalEventRecorder);
            eventThrowExecutor.setEngine(kikwiflowEngine);

            return new TestEngine(kikwiflowEngine, assertableKikwiEngine, processDefinitionService);
        }
    }
}
