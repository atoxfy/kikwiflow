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
 * Registrado por variável alterada via {@code KikwiflowEngine.setVariables}/{@code unsetVariables}. Grava o
 * valor bruto, sem aplicar {@code VariableSecurityPolicyManager} — masking é responsabilidade de quem
 * consumir o outbox (relay/consumer), não do produtor do evento. {@code removed} distingue uma remoção
 * (via {@code unsetVariables}, {@code value} sempre {@code null}) de um valor setado explicitamente para
 * {@code null} — sem essa flag as duas situações seriam indistinguíveis no histórico.
 */
public record ProcessVariableChanged(
        String processInstanceId,
        String processDefinitionId,
        String tenantId,
        String name,
        boolean isTransient,
        Object value,
        String actorId,
        Instant changedAt,
        boolean removed
) implements CriticalEvent {}
