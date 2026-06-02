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

import io.kikwiflow.api.query.StatsQueryApi;
import io.kikwiflow.management.controller.stats.mapper.ProcessMapper;
import io.kikwiflow.management.controller.stats.response.KKFProcess;
import io.kikwiflow.management.controller.stats.response.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RequestMapping("${kikwiflow.api.base-path:/engine/api/v1}/pulse")
public class StatsQueryController {

    private final QueryRepository queryRepository;

    public StatsQueryController(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @GetMapping(value = "pulse/stream/process-definition/{processDefinitionId}/snapshot")
    public SseEmitter streamSnapshotSse(@PathVariable String processDefinitionId) {

        SseEmitter emitter = new SseEmitter(0L);
        Thread.startVirtualThread(() -> {
            boolean isConnected = true;

            while (isConnected) {
                try {
                    KKFProcess snapshot = buildProcessSnapshot(processDefinitionId);

                    emitter.send(snapshot);

                    Thread.sleep(5000);

                } catch (IOException e) {
                    isConnected = false;
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    isConnected = false;
                }
            }
        });

        emitter.onCompletion(() -> System.out.println("SSE finished for " + processDefinitionId));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }


    @GetMapping("process-definition/{processDefinitionId}/snapshot")
    public KKFProcess getSnapshot(String processDefinitionId) {
        return buildProcessSnapshot(processDefinitionId);
    }

    private KKFProcess buildProcessSnapshot(String processDefinitionId) {
        return queryRepository.findProcessDefinitionById(processDefinitionId)
                .map(definition -> {
                    KKFMetrics macroMetrics = queryRepository.getProcessMacroMetrics(processDefinitionId);
                    Map<String, KKFMetrics> nodeMetrics = queryRepository.getMetricsByNodeForProcessDefinition(processDefinitionId);
                    final Map<String, KKFFlowNodeDefinition> flowNodes = new HashMap<>();

                    definition.flowNodes().values().forEach(node -> {
                        KKFMetrics metrics = nodeMetrics.getOrDefault(node.id(), new KKFMetrics(0L, 100.00, 0L));

                        if (node instanceof ExecutableTaskDefinition || node instanceof ExternalTaskDefinition) {
                            flowNodes.put(node.id(), ProcessMapper.mapNode(node, metrics));
                        } else {
                            flowNodes.put(node.id(), ProcessMapper.mapNode(node, null));
                        }
                    });

                    return new KKFProcess(
                            definition.id(), definition.key(), definition.name(),
                            definition.description(), definition.sla(), macroMetrics, definition.checksum(),
                            flowNodes, definition.defaultStartPoint(), definition.extensionProperties()
                    );
                }).orElseThrow(() -> new NotFoundException("Process Not Found With id " + processDefinitionId));
    }
}
