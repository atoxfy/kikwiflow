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
import io.kikwiflow.management.dtos.KKFProcessStats;
import io.kikwiflow.management.service.StatsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@KikwiRestController
@RequestMapping("/pulse")
public class StatsSSEQueryController {

    private final StatsService statsService;
    private final ExecutorService sseExecutor;
    private final long updateIntervalMillis;

    private final Map<String, Map<String, SseEmitter>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public StatsSSEQueryController(
            StatsService statsService,
            @Qualifier("kikwiflowRestExecutor") ExecutorService sseExecutor,
            long updateIntervalMillis) {
        this.statsService = statsService;
        this.sseExecutor = sseExecutor;
        this.updateIntervalMillis = updateIntervalMillis;
    }

    @GetMapping(value = "/process-definition/{processDefinitionId}/snapshot/stream")
    public SseEmitter streamSnapshotSse(@PathVariable(value = "processDefinitionId") String processDefinitionId) {
        SseEmitter emitter = new SseEmitter(0L);
        String emitterId = java.util.UUID.randomUUID().toString();

        subscriptions.computeIfAbsent(processDefinitionId, k -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);

        Runnable onDisconnect = () -> {
            Map<String, SseEmitter> group = subscriptions.get(processDefinitionId);
            if (group != null) {
                group.remove(emitterId);
                if (group.isEmpty()) {
                    subscriptions.remove(processDefinitionId);
                    cancelPolling(processDefinitionId);
                }
            }
        };

        emitter.onCompletion(onDisconnect);
        emitter.onTimeout(onDisconnect);
        emitter.onError(e -> onDisconnect.run());

        startPollingIfNeeded(processDefinitionId);

        return emitter;
    }

    private synchronized void startPollingIfNeeded(String processDefinitionId) {
        if (!runningTasks.containsKey(processDefinitionId)) {
            Future<?> task = sseExecutor.submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Map<String, SseEmitter> emitters = subscriptions.get(processDefinitionId);
                        if (emitters == null || emitters.isEmpty()) {
                            break;
                        }

                        KKFProcessStats snapshot = statsService.buildProcessSnapshot(processDefinitionId);
                        emitters.forEach((id, emitter) -> {
                            try {
                                emitter.send(snapshot);
                            } catch (IOException e) {
                                emitter.complete();
                            }
                        });

                        Thread.sleep(updateIntervalMillis);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    cancelPolling(processDefinitionId);
                }
            });

            runningTasks.put(processDefinitionId, task);
        }
    }

    private synchronized void cancelPolling(String processDefinitionId) {
        Future<?> task = runningTasks.remove(processDefinitionId);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }
}
