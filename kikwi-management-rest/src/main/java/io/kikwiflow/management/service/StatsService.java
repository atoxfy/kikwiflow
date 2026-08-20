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

package io.kikwiflow.management.service;

import io.kikwiflow.management.controller.stats.mapper.ProcessMapper;
import io.kikwiflow.management.dtos.KKFProcessStats;
import io.kikwiflow.management.dtos.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StatsService {
    private final QueryRepository queryRepository;

    public StatsService(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public KKFProcessStats buildProcessSnapshot(String processDefinitionId) {
        return queryRepository.findProcessDefinitionById(processDefinitionId)
                .map(definition -> {
                    KKFMetrics macroMetrics = queryRepository.getProcessMacroMetrics(processDefinitionId);
                    Map<String, KKFMetrics> nodeMetrics = queryRepository.getMetricsByNodeForProcessDefinition(processDefinitionId);
                    final Map<String, KKFFlowNodeDefinition> flowNodes = new HashMap<>();

                    definition.flowNodes().values().forEach(node -> {
                        KKFMetrics metrics = nodeMetrics.getOrDefault(node.id(), new KKFMetrics(0L, 100.00, 0L));

                        // CALL_ACTIVITY_COORDINATOR e TIMER_TASK também são materializados como ExecutableTask
                        // (mesma coleção agregada por getMetricsByNodeForProcessDefinition, chaveada por
                        // taskDefinitionId) — "quantas instâncias estão paradas aqui" faz sentido para os dois
                        // tanto quanto para EXECUTABLE_TASK/EXTERNAL_TASK/EVENT_CATCHER. Historicamente esses
                        // dois tipos foram adicionados ao ProcessMapper só para não quebrar o endpoint
                        // (ProcessMapperTest), sem estender esse enriquecimento de métricas junto — por isso um
                        // CALL_ACTIVITY_COORDINATOR aparecia em flowNodes sem o campo metrics, mesmo com dados
                        // reais disponíveis em nodeMetrics.
                        if (node instanceof ExecutableTaskDefinition || node instanceof ExternalTaskDefinition
                                || node instanceof EventCatcherDefinition || node instanceof CallActivityDefinition
                                || node instanceof TimerTaskDefinition) {
                            flowNodes.put(node.id(), ProcessMapper.mapNode(node, metrics));
                        } else {
                            flowNodes.put(node.id(), ProcessMapper.mapNode(node, null));
                        }
                    });

                    return new KKFProcessStats(
                            definition.id(), definition.key(), definition.name(),
                            definition.description(), definition.sla(), macroMetrics, definition.checksum(),
                            flowNodes, definition.defaultStartPoint(), definition.extensionProperties()
                    );
                }).orElseThrow(() -> new NotFoundException("Process Not Found With id " + processDefinitionId));
    }
}
