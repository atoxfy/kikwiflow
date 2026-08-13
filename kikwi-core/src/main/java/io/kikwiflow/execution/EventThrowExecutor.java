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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.execution.api.dto.CorrelationItem;
import io.kikwiflow.execution.evaluator.CorrelationKeyResolver;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;

import java.util.List;
import java.util.Map;

/**
 * Executa o efeito de um nó {@link EventThrowerDefinition}: resolve a chave de correlação (mesmo mecanismo de
 * {@link CorrelationKeyResolver} usado pelo lado catch) e entrega internamente pelo mesmo caminho de
 * {@code KikwiflowEngine.correlateMessage} — {@link KikwiflowEngine#correlateFromThrow}.
 *
 * <p>A referência a {@link KikwiflowEngine} é atribuída depois da construção (ver {@link #setEngine}) em vez de
 * vir pelo construtor: {@code KikwiflowEngine} depende de {@code ProcessExecutionManager}, que depende de
 * {@link FlowNodeExecutor}, que depende desta classe — um construtor circular. O mesmo padrão já é usado por
 * {@link TaskAcquirer#start(KikwiflowEngine)} no motor por exatamente o mesmo motivo.
 */
public class EventThrowExecutor {

    private final CorrelationKeyResolver correlationKeyResolver;
    private KikwiflowEngine engine;

    public EventThrowExecutor(CorrelationKeyResolver correlationKeyResolver) {
        this.correlationKeyResolver = correlationKeyResolver;
    }

    public void setEngine(KikwiflowEngine engine) {
        this.engine = engine;
    }

    public void throwEvent(EventThrowerDefinition thrower, ProcessInstanceExecution execution) {
        List<CorrelationItem> items = correlationKeyResolver.resolve(thrower, execution);
        CorrelationItem item = items.get(0);
        engine.correlateFromThrow(item.key(), execution.getTenantId(), Map.of());
    }
}
