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
 * {@code assignee} é para quem a tarefa foi atribuída; {@code actorId} é quem executou a ação de claim — na
 * prática normalmente a mesma pessoa, mas podem divergir (ex.: um supervisor atribuindo em nome de outro).
 */
public record ExternalTaskClaimed(
        String externalTaskId,
        String processDefinitionId,
        String processInstanceId,
        String tenantId,
        String taskDefinitionId,
        String assignee,
        String actorId,
        Instant claimedAt
) implements CriticalEvent {}
