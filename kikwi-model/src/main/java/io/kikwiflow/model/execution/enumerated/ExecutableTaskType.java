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

package io.kikwiflow.model.execution.enumerated;

public enum ExecutableTaskType {
    STANDARD,
    INTERRUPTIVE_TIMER,
    JOIN_GATEWAY,
    NON_INTERRUPTIVE_TIMER,
    CALL_ACTIVITY_COORDINATOR,
    /**
     * A "iniciadora" gerada uma por elemento (ou uma única, sem {@code collectionVariable}) quando um
     * {@code CALL_ACTIVITY_COORDINATOR} é alcançado. Ao ser adquirida, chama {@code KikwiflowEngine.startProcess()}
     * para o {@code calledElement} e se auto-apaga — nunca gera continuação no fluxo do pai (ver
     * {@code KikwiflowEngine.executeFromTask}). {@code joinTaskId} aponta para a coordenadora, {@code branchId}
     * é {@code coordinatorTaskId + ":" + loopIndex}.
     */
    CALL_ACTIVITY_STARTER,
    /**
     * {@code TimerTaskDefinition} — nó de fluxo principal (não um evento de borda) que pausa até um
     * {@code dueDate} calculado e então continua pelas próprias arestas de saída.
     */
    TIMER_TASK,
    /**
     * {@code EventThrowerDefinition} — nó de fluxo principal que lança um evento correlacionado. Só existe
     * como {@code ExecutableTask} persistida quando o nó é alcançado de forma assíncrona (ex.: {@code
     * commitBefore: true} nele ou no nó anterior); nesse caso o efeito de lançar é executado quando esta
     * tarefa é retomada via {@code KikwiflowEngine.executeFromTask}, com o mesmo retry/incident de qualquer
     * outra {@code ExecutableTask} em falha.
     */
    EVENT_THROW
}
