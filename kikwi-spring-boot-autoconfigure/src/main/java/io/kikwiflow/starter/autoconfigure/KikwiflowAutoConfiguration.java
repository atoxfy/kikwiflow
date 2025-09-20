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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.execution.ContinuationService;
import io.kikwiflow.execution.DecisionRuleResolver;
import io.kikwiflow.execution.DelegateResolver;
import io.kikwiflow.execution.FlowNodeExecutor;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.TaskExecutor;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KikwiflowEngine.class)
@EnableConfigurationProperties(KikwiflowProperties.class)
public class KikwiflowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BpmnParser bpmnParser() {
        return new DefaultBpmnParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessDefinitionService processDefinitionService(BpmnParser bpmnParser, KikwiEngineRepository repository) {
        return new ProcessDefinitionService(bpmnParser, repository);
    }

    @Bean
    @ConditionalOnMissingBean
    public Navigator navigator(DecisionRuleResolver decisionRuleResolver) {
        return new Navigator(decisionRuleResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessExecutionManager processExecutionManager(DelegateResolver delegateResolver, Navigator navigator, KikwiflowConfig config) {
        return new ProcessExecutionManager(new FlowNodeExecutor(new TaskExecutor(delegateResolver)), navigator, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContinuationService continuationService(KikwiEngineRepository repository, KikwiflowConfig config) {
        return new ContinuationService(repository, config);
    }

    @Bean
    @ConditionalOnMissingBean
    public KikwiflowConfig kikwiflowConfig(KikwiflowProperties properties) {
        KikwiflowConfig config = new KikwiflowConfig();
        if (properties.getStats().isEnabled()) {
            config.statsEnabled();
        }
        if (properties.getOutbox().isEventsEnabled()) {
            config.outboxEventsEnabled();
        }
        return config;
    }


    @Bean
    @ConditionalOnMissingBean
    public DelegateResolver delegateResolver(ApplicationContext applicationContext) {
        return new SpringDelegateResolver(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionRuleResolver decisionRuleResolver(ApplicationContext applicationContext){
        return new SpringDecisionRuleResolver(applicationContext);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    public KikwiflowEngine kikwiflowEngine(
            KikwiEngineRepository repository,
            KikwiflowConfig config,
            DelegateResolver delegateResolver,
            DecisionRuleResolver decisionRuleResolver,
            ObjectProvider<List<ExecutionEventListener>> listenersProvider,
            ProcessDefinitionService processDefinitionService,
            Navigator navigator,
            ProcessExecutionManager processExecutionManager) {

        // O Spring injetará automaticamente uma lista de todos os beans
        // do tipo ExecutionEventListener que o usuário tenha definido.
        List<ExecutionEventListener> listeners = listenersProvider.getIfAvailable(Collections::emptyList);

        return new KikwiflowEngine(processDefinitionService, navigator,
                processExecutionManager, repository, config, listeners);
    }
}