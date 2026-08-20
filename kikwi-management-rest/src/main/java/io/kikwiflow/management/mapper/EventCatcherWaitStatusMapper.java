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

package io.kikwiflow.management.mapper;

import io.kikwiflow.management.dtos.KKFEventCatcherWaitStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.node.ExternalTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Deriva o progresso "N de M eventos recebidos" de um {@code EVENT_CATCHER} a partir da lista crua de
 * {@code ExternalTask} de uma instância — sem consulta adicional, os dados já vêm juntos no snapshot
 * ({@code ProcessInstanceSnapshotService.getSnapshot}). Ver docs/engine/21-...md itens 10/11 e
 * {@link KKFEventCatcherWaitStatus}.
 */
public final class EventCatcherWaitStatusMapper {

    private EventCatcherWaitStatusMapper() {}

    public static List<KKFEventCatcherWaitStatus> compute(List<ExternalTask> externalTasks) {
        List<KKFEventCatcherWaitStatus> result = new ArrayList<>();
        if (externalTasks == null || externalTasks.isEmpty()) {
            return result;
        }

        for (ExternalTask task : externalTasks) {
            if (task.type() == ExternalTaskType.EVENT_CATCHER_STANDALONE) {
                result.add(new KKFEventCatcherWaitStatus(
                        task.taskDefinitionId(),
                        task.id(),
                        null,
                        1,
                        0,
                        List.of(task.correlationKey())
                ));
            } else if (task.type() == ExternalTaskType.EVENT_CATCHER_PARENT) {
                List<ExternalTask> children = externalTasks.stream()
                        .filter(t -> t.type() == ExternalTaskType.EVENT_CATCHER_CHILD
                                && task.id().equals(t.coordinatorTaskId()))
                        .toList();

                List<String> pending = children.stream()
                        .filter(c -> c.status() != ExternalTaskStatus.CORRELATED)
                        .map(ExternalTask::correlationKey)
                        .toList();

                int total = !children.isEmpty() ? children.size()
                        : (task.totalCorrelationKeys() != null ? task.totalCorrelationKeys() : 0);
                int received = !children.isEmpty() ? children.size() - pending.size() : 0;

                result.add(new KKFEventCatcherWaitStatus(
                        task.taskDefinitionId(),
                        task.id(),
                        task.matchPolicy(),
                        total,
                        received,
                        !children.isEmpty() ? pending : task.pendingCorrelationKeys()
                ));
            }
        }

        return result;
    }
}
