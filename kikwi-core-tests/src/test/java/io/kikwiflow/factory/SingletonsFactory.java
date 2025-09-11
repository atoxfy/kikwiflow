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

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.*;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

public class SingletonsFactory {

    public static BpmnParser bpmnParser() {
        return new DefaultBpmnParser();
    }

    public static ProcessDefinitionService processDefinitionService(BpmnParser bpmnParser, KikwiEngineRepository repository) {
        return new ProcessDefinitionService(bpmnParser, repository);
    }

    public static Navigator navigator(DecisionRuleResolver decisionRuleResolver) {
        return new Navigator(decisionRuleResolver);
    }

    public static ProcessExecutionManager processExecutionManager(DelegateResolver delegateResolver, Navigator navigator, KikwiflowConfig config) {
        return new ProcessExecutionManager(new FlowNodeExecutor(new TaskExecutor(delegateResolver)), navigator, config);
    }

    public static ContinuationService continuationService(KikwiEngineRepository repository) {
        return new ContinuationService(repository);
    }
}
