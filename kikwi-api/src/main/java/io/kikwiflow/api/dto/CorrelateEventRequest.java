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
 * Corpo de {@code POST /events/correlate/{correlationKey}} — a chave de correlação em si vai na URL (mesmo
 * padrão de {@code PUT /external-tasks/{id}/claim/{assignee}}), este DTO carrega só as variáveis entregues
 * junto com o evento externo.
 */
public record CorrelateEventRequest(
        Map<String, ProcessVariable> variables
) {
}
