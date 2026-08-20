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
 * Emitido quando uma {@code ProcessInstance} filha de um {@code CALL_ACTIVITY_COORDINATOR} conclui
 * ({@code COMPLETED}) mas a tarefa coordenadora do pai ({@code joinTaskId}) já não existe mais no momento do
 * {@code $pull} — a coordenadora foi apagada antes (timeout do boundary event, ver
 * {@code docs/engine/20-subprocessos-call-activity-especificacao.md}, §5). O motor nunca mata a instância
 * filha nesse caso (ela já concluiu por conta própria); este evento existe só para que a situação seja
 * visível em vez de um {@code $pull}/lookup silenciosamente casando zero documentos. {@code processInstanceId}/
 * {@code processDefinitionId}/{@code tenantId} identificam a instância <b>filha</b> (quem gerou o evento),
 * não o pai.
 */
public record OrphanedChildCompletion(
        String processInstanceId,
        String processDefinitionId,
        String tenantId,
        String parentInstanceId,
        String joinTaskId,
        String branchId,
        Instant occurredAt
) implements CriticalEvent {}
