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

package io.kikwiflow.model.execution;

import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.time.Instant;
import java.util.Map;

/**
 * {@code parentInstanceId}/{@code callerTaskId}/{@code callerBranchId} espelham os campos homônimos de
 * {@link ProcessInstance} — {@code null} numa instância "raiz" (não iniciada por um {@code
 * CALL_ACTIVITY_COORDINATOR}), preenchidos numa instância filha de subprocesso. Expostos aqui (e não só na
 * {@code ProcessInstance} completa) para que o monitor veja a relação pai/filho já na tela de lista/busca
 * ({@code POST /process-instances/search}), sem precisar buscar cada instância individualmente — ver
 * docs/engine/21-revisao-observabilidade-e-performance-monitor.md item 11.
 */
public record ProcessInstanceSummary(
        String id,
        String businessKey,
        ProcessInstanceStatus status,
        String processDefinitionId,
        Instant startedAt,
        Instant endedAt,
        Map<String, Integer> activeNodes,
        String parentInstanceId,
        String callerTaskId,
        String callerBranchId
) {}
