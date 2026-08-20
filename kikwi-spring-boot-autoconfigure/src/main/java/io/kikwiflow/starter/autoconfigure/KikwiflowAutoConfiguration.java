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
package io.kikwiflow.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.execution.ContinuationService;
import io.kikwiflow.execution.EventThrowExecutor;
import io.kikwiflow.execution.FailureHandler;
import io.kikwiflow.execution.FlowNodeExecutor;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.event.CriticalEventRecorder;
import io.kikwiflow.execution.TaskAcquirer;
import io.kikwiflow.execution.TaskExecutor;
import io.kikwiflow.execution.TaskHandlerResolver;
import io.kikwiflow.execution.api.parser.ProcessDefinitionParser;
import io.kikwiflow.execution.api.resolver.AnswerProviderResolver;
import io.kikwiflow.execution.api.resolver.CorrelationKeysProviderResolver;
import io.kikwiflow.execution.api.resolver.DueDateProviderResolver;
import io.kikwiflow.execution.api.retry.RetryPolicyEvaluator;
import io.kikwiflow.execution.evaluator.CorrelationKeyResolver;
import io.kikwiflow.execution.evaluator.TimerDueDateEvaluator;
import io.kikwiflow.execution.policy.DefaultRetryPolicyEvaluator;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.parser.jackson.JacksonProcessDefinitionParser;
import io.kikwiflow.parser.jackson.KikwiflowJacksonModule;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.security.DefaultDeploymentSecurityManager;
import io.kikwiflow.security.DefaultVariableSecurityPolicyManager;
import io.kikwiflow.security.api.DeploymentSecurityManager;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import io.kikwiflow.starter.autoconfigure.resolvers.SpringAnswerProviderResolver;
import io.kikwiflow.starter.autoconfigure.resolvers.SpringCorrelationKeysProviderResolver;
import io.kikwiflow.starter.autoconfigure.resolvers.SpringDueDateProviderResolver;
import io.kikwiflow.starter.autoconfigure.resolvers.SpringTaskHandlerResolver;
import io.kikwiflow.validation.DeployValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KikwiflowEngine.class)
@EnableConfigurationProperties(KikwiflowProperties.class)
public class KikwiflowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KikwiflowConfig kikwiflowConfig(KikwiflowProperties properties) {
        KikwiflowConfig config = new KikwiflowConfig();
        config.setInstanceName(properties.getInstanceName());
        if (properties.getStats().isEnabled()) {
            config.statsEnabled();
        }

        if (properties.getOutbox().isEventsEnabled()) {
            config.outboxEventsEnabled();
        }

        if(properties.getExecution() != null){
            config.setTaskAcquisitionMaxTasks(properties.getExecution().getTaskAcquisitionMaxTasks());
            config.setTaskAcquisitionIntervalMillis(properties.getExecution().getTaskAcquisitionIntervalMillis());
            config.setMaxConcurrentTasks(properties.getExecution().getMaxConcurrentTasks());
            config.setShutdownGracePeriodSeconds(properties.getExecution().getShutdownGracePeriodSeconds());
            config.setLockTimeoutMillis(properties.getExecution().getLockTimeoutMillis());
        }

        if (properties.getRetry() != null ){

            if(properties.getRetry().getDefaultRetryInterval() != null){
                config.setDefaultRetryInterval(properties.getRetry().getDefaultRetryInterval());
            }

            if(properties.getRetry().getFatalExceptions() != null) {
                config.setFatalExceptions(properties.getRetry().getFatalExceptions());
            }
        }


        if(properties.getSecurity() != null){
            config.setProcessDefinitionDeployEnabled(properties.getSecurity().isDeployEnabled());
        }

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public DeployValidator deployValidator(TaskHandlerResolver taskHandlerResolver, AnswerProviderResolver answerProviderResolver,
                                            CorrelationKeysProviderResolver correlationKeysProviderResolver){
        return new DeployValidator(taskHandlerResolver, answerProviderResolver, correlationKeysProviderResolver);
    }

    @Bean
    @ConditionalOnMissingBean(KikwiflowJacksonModule.class)
    public KikwiflowJacksonModule kikwiflowJacksonModule() {
        return new KikwiflowJacksonModule();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDefinitionParser.class)
    public ProcessDefinitionParser processDefinitionParser(ObjectMapper objectMapper) {
        return new JacksonProcessDefinitionParser(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeploymentSecurityManager deploymentSecurityManager(KikwiflowConfig kikwiflowConfig){
        return new DefaultDeploymentSecurityManager(kikwiflowConfig.isProcessDefinitionDeployEnabled());
    }

    /**
     * Sem implementação própria registrada, o comportamento OSS é permissivo (no-op — ver
     * {@link DefaultVariableSecurityPolicyManager}). Aplicações que precisam de RBAC/masking real sobre
     * variáveis de processo devem registrar seu próprio bean {@link VariableSecurityPolicyManager}, que
     * vence aqui via {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean
    public VariableSecurityPolicyManager variableSecurityPolicyManager(){
        return new DefaultVariableSecurityPolicyManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionService processDefinitionService(ProcessDefinitionParser parser, KikwiEngineRepository repository, DeployValidator deployValidator, DeploymentSecurityManager deploymentSecurityManager) {
        return new ProcessDefinitionService(parser, repository, deployValidator, deploymentSecurityManager);
    }


    @Bean
    @ConditionalOnProperty(prefix = "kikwiflow.process-definition.auto-deploy", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KikwiflowAutoDeployer kikwiflowAutoDeployer(
            ProcessDefinitionService processDefinitionService,
            ResourcePatternResolver resourcePatternResolver,
            KikwiflowProperties properties) {

        return new KikwiflowAutoDeployer(
                processDefinitionService ,
                resourcePatternResolver,
                properties.getProcessDefinition().getAutoDeploy().getPath()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public Navigator navigator(AnswerProviderResolver answerProviderResolver) {
        return new Navigator(answerProviderResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public CriticalEventRecorder criticalEventRecorder(KikwiflowConfig config) {
        return new CriticalEventRecorder(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventThrowExecutor eventThrowExecutor(CorrelationKeyResolver correlationKeyResolver) {
        return new EventThrowExecutor(correlationKeyResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessExecutionManager processExecutionManager(TaskHandlerResolver taskHandlerResolver, Navigator navigator,
            CriticalEventRecorder criticalEventRecorder, EventThrowExecutor eventThrowExecutor) {
        return new ProcessExecutionManager(new FlowNodeExecutor(new TaskExecutor(taskHandlerResolver), eventThrowExecutor), navigator, criticalEventRecorder);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContinuationService continuationService(KikwiEngineRepository repository, KikwiflowConfig config,
            TimerDueDateEvaluator dueDateResolver, CorrelationKeyResolver correlationKeyResolver,
            CriticalEventRecorder criticalEventRecorder) {
        return new ContinuationService(repository, dueDateResolver, correlationKeyResolver, config, criticalEventRecorder);
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationKeysProviderResolver correlationKeysProviderResolver(ApplicationContext applicationContext) {
        return new SpringCorrelationKeysProviderResolver(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationKeyResolver correlationKeyResolver(CorrelationKeysProviderResolver correlationKeysProviderResolver) {
        return new CorrelationKeyResolver(correlationKeysProviderResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskHandlerResolver taskHandlerResolver(ApplicationContext applicationContext) {
        return new SpringTaskHandlerResolver(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnswerProviderResolver answerProviderResolver(ApplicationContext applicationContext){
        return new SpringAnswerProviderResolver(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public DueDateProviderResolver dueDateProviderResolver(ApplicationContext applicationContext){
        return new SpringDueDateProviderResolver(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(RetryPolicyEvaluator.class)
    public RetryPolicyEvaluator retryPolicyEvaluator(KikwiflowConfig kikwiflowConfig) {
        return new DefaultRetryPolicyEvaluator(kikwiflowConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public TimerDueDateEvaluator timerDueDateResolver(DueDateProviderResolver dueDateProviderResolver) {
        return new TimerDueDateEvaluator(dueDateProviderResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public FailureHandler failureHandler(KikwiEngineRepository repository, RetryPolicyEvaluator retryEvaluator, CriticalEventRecorder criticalEventRecorder) {
        return new FailureHandler(repository, retryEvaluator, criticalEventRecorder);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsynchronousEventPublisher asynchronousEventPublisher(ObjectProvider<List<ExecutionEventListener>> listenersProvider) {
        AsynchronousEventPublisher publisher = new AsynchronousEventPublisher(Executors.newVirtualThreadPerTaskExecutor());
        List<ExecutionEventListener> listeners = listenersProvider.getIfAvailable(Collections::emptyList);
        listeners.forEach(publisher::registerListener);
        return publisher;
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskAcquirer taskAcquirer(KikwiEngineRepository repository, KikwiflowConfig config) {
        return new TaskAcquirer(repository, config);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public KikwiflowEngine kikwiflowEngine(
            ProcessDefinitionService processDefinitionService,
            Navigator navigator,
            ProcessExecutionManager processExecutionManager,
            KikwiEngineRepository repository,
            KikwiflowConfig config,
            AsynchronousEventPublisher eventPublisher,
            ContinuationService continuationService,
            FailureHandler failureHandler,
            TaskAcquirer taskAcquirer,
            CriticalEventRecorder criticalEventRecorder,
            EventThrowExecutor eventThrowExecutor) {

        KikwiflowEngine engine = new KikwiflowEngine(
                processDefinitionService,
                navigator,
                processExecutionManager,
                repository,
                config,
                eventPublisher,
                continuationService,
                failureHandler,
                taskAcquirer,
                criticalEventRecorder
        );

        // Quebra o ciclo construtor KikwiflowEngine -> ProcessExecutionManager -> FlowNodeExecutor ->
        // EventThrowExecutor -> KikwiflowEngine: atribuído depois que o engine já existe, mesmo padrão de
        // TaskAcquirer.start(engine) logo abaixo.
        eventThrowExecutor.setEngine(engine);

        return engine;
    }
}