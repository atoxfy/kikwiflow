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

package io.kikwiflow.management.controller.stats;

import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.controller.stats.mapper.ProcessMapper;
import io.kikwiflow.management.controller.stats.response.KKFProcess;
import io.kikwiflow.management.controller.stats.response.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.service.StatsService;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@KikwiRestController
@RequestMapping("/pulse")
public class StatsSSEQueryController {

    private final StatsService statsService;
    private final ExecutorService sseExecutor;
    private final long updateIntervalMillis;

    public StatsSSEQueryController(
            StatsService statsService, @Qualifier("kikwiflowRestExecutor") ExecutorService sseExecutor,
            long updateIntervalMillis) {

        this.statsService = statsService;
        this.sseExecutor = sseExecutor;
        this.updateIntervalMillis = updateIntervalMillis;
    }


    @GetMapping(value = "/process-definition/{processDefinitionId}/snapshot/stream")
    public SseEmitter streamSnapshotSse(@PathVariable String processDefinitionId) {

        SseEmitter emitter = new SseEmitter(0L);
        sseExecutor.submit(() -> {
            boolean isConnected = true;

            while (isConnected) {
                try {
                    KKFProcess snapshot = statsService.buildProcessSnapshot(processDefinitionId);
                    emitter.send(snapshot);
                    Thread.sleep(updateIntervalMillis);

                } catch (IOException e) {
                    isConnected = false;
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    isConnected = false;
                }
            }
        });

        //TODO add logger
        emitter.onCompletion(() -> System.out.println("SSE finished for " + processDefinitionId));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }
}
