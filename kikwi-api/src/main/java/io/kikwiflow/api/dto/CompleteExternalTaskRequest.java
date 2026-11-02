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

package io.kikwiflow.api.dto;

import io.kikwiflow.model.execution.ProcessVariable;

import java.util.Map;

/**
 * @param workerId      dono do lease (docs/engine/31, E2): obrigatório para concluir uma missão de
 *                      {@code AGENT_TASK}; ausente = complete humano de sempre
 * @param missionReport resumo opcional do runtime de agentes (ex.: {@code turns}, {@code usage}), gravado no
 *                      {@code AGENT_MISSION_COMPLETED}
 */
public record CompleteExternalTaskRequest(
        String tenant,
        String targetFlowNodeId,
        Map<String, ProcessVariable> variables,
        String workerId,
        Map<String, Object> missionReport
) {
}
