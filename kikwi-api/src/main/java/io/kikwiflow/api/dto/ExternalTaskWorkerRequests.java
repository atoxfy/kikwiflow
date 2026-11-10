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

import io.kikwiflow.model.execution.AgentActivityReport;
import io.kikwiflow.model.execution.ProcessVariable;

import java.util.List;
import java.util.Map;

/**
 * Corpos do protocolo de worker com lease (docs/engine/31, E2/E3). Durações são ISO-8601 ({@code PT5M}).
 */
public final class ExternalTaskWorkerRequests {

    private ExternalTaskWorkerRequests() {}

    /** @param maxTasks {@code null} = 1 */
    public record FetchAndLock(String workerId, List<String> topics, Integer maxTasks, String lockDuration) {
    }

    public record ExtendLock(String workerId, String lockDuration) {
    }

    /** Trava uma tarefa escolhida pelo worker (ex.: de uma lista de missões disponíveis). */
    public record Lock(String workerId, String lockDuration) {
    }

    /**
     * @param retries      tentativas que restam: {@code 0} abre incidente
     * @param retryTimeout quando a tarefa volta a ser adquirível; {@code null} = já
     */
    public record Failure(String workerId, String errorMessage, String errorDetails, Integer retries,
                          String retryTimeout) {
    }

    public record BusinessError(String workerId, String errorCode, String errorMessage,
                                Map<String, ProcessVariable> variables) {
    }

    public record ReportActivities(String workerId, List<AgentActivityReport> activities) {
    }
}
