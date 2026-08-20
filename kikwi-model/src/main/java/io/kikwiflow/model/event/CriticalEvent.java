/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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

public interface CriticalEvent {

    String processInstanceId();

    String processDefinitionId();

    String tenantId();

    /**
     * Quem comandou a ação que gerou este evento. {@code null} para os tipos que ainda não carregam essa
     * informação ({@link FlowNodeFinished}, {@link GatewayAnswerResolved}, {@link ProcessInstanceFinished}) —
     * ver a nota em {@code docs/engine/09-eventos-e-observabilidade.md} sobre o porquê desses três ainda
     * ficarem de fora.
     */
    default String actorId() {
        return null;
    }
}
