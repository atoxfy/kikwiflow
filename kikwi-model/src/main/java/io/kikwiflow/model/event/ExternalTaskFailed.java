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

package io.kikwiflow.model.event;

import java.time.Instant;

/**
 * O worker reportou falha técnica ({@code handleFailure}, docs/engine/31 E2). Com {@code remainingRetries > 0} a
 * tarefa volta a ser adquirível em {@code availableAt}; com {@code 0} um {@code INCIDENT_CREATED} sai no mesmo
 * commit.
 */
public record ExternalTaskFailed(
        String externalTaskId,
        String processDefinitionId,
        String processInstanceId,
        String tenantId,
        String taskDefinitionId,
        String workerId,
        String errorMessage,
        int remainingRetries,
        Instant availableAt,
        Instant failedAt
) implements CriticalEvent {

    @Override
    public String actorId() {
        return workerId;
    }
}
