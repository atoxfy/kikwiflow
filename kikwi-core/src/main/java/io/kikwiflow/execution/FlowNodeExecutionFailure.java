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
package io.kikwiflow.execution;

import io.kikwiflow.model.event.OutboxEventEntity;

import java.util.List;

/**
 * Envelope interno lançado por {@link ProcessExecutionManager#executeFlow} quando um nó falha.
 * <p>
 * Carrega, além da causa original ({@link #getCause()}), os outbox events já construídos durante a mesma
 * chamada — incluindo o {@code FLOW_NODE_FINISHED(ERROR)} do próprio nó que falhou — para que o chamador
 * possa persisti-los junto com o resultado do tratamento de falha (retry/incidente) em vez de descartá-los
 * ao propagar a exceção. Os chamadores de {@code executeFlow} devem desembrulhar esta exceção (extraindo a
 * causa original e os eventos) antes de repassá-la adiante, para preservar o tipo/mensagem da exceção de
 * negócio original para o restante do motor.
 */
public class FlowNodeExecutionFailure extends RuntimeException {

    private final List<OutboxEventEntity> criticalEvents;

    public FlowNodeExecutionFailure(RuntimeException cause, List<OutboxEventEntity> criticalEvents) {
        super(cause.getMessage(), cause);
        this.criticalEvents = criticalEvents;
    }

    public List<OutboxEventEntity> getCriticalEvents() {
        return criticalEvents;
    }
}
