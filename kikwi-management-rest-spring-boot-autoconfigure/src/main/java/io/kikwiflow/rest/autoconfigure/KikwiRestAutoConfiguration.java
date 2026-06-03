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

package io.kikwiflow.rest.autoconfigure;

import io.kikwiflow.management.controller.externaltask.ExternalTaskCommandController;
import io.kikwiflow.management.controller.externaltask.ExternalTaskQueryController;
import io.kikwiflow.management.controller.processdefinition.ProcessDefinitionCommandController;
import io.kikwiflow.management.controller.processdefinition.ProcessDefinitionQueryController;
import io.kikwiflow.management.controller.processinstance.ProcessInstanceCommandController;
import io.kikwiflow.management.controller.processinstance.ProcessInstanceQueryController;
import io.kikwiflow.management.controller.stats.StatsQueryController;
import io.kikwiflow.management.controller.stats.StatsSSEQueryController;
import io.kikwiflow.management.service.StatsService;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.rest.autoconfigure.properties.KikwiflowPulseProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AutoConfiguration(
        afterName = "io.kikwiflow.starter.autoconfigure.KikwiflowAutoConfiguration"
)
@ConditionalOnClass(ProcessDefinitionQueryController.class)
@EnableConfigurationProperties(KikwiflowPulseProperties.class)
@Import({
        ProcessDefinitionQueryController.class,
        ProcessInstanceQueryController.class,
        ExternalTaskQueryController.class,
        StatsQueryController.class,

        StatsService.class,

        ProcessDefinitionCommandController.class,
        ProcessInstanceCommandController.class,
        ExternalTaskCommandController.class
})
public class KikwiRestAutoConfiguration {

    @Bean(name = "kikwiflowRestExecutor")
    @ConditionalOnMissingBean(name = "kikwiflowRestExecutor")
    @ConditionalOnProperty(prefix = "kikwiflow.pulse.sse-endpoints", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ExecutorService kikwiflowRestExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kikwiflow.pulse.sse-endpoints", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StatsSSEQueryController statsSSEQueryController(
            StatsService statsService,
            @Qualifier("kikwiflowRestExecutor") ExecutorService sseExecutor,
            KikwiflowPulseProperties properties) {

        return new StatsSSEQueryController(statsService, sseExecutor, properties.interval());
    }
}