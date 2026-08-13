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

package io.kikwiflow.model.execution.node;

/**
 * Representa a referência de um evento anexado a um nó (como um Boundary Event).
 * Mantém a coesão entre a instância em execução e a sua definição original.
 *
 * @param instanceType Em qual coleção {@code instanceId} vive — {@code EXECUTABLE_TASK} para timers de borda,
 *                     {@code EXTERNAL_TASK} para um {@code InterruptiveCatchEventDefinition} anexado. Necessário
 *                     porque, ao contrário de timers, um catch event de borda é uma espera de correlação
 *                     (ExternalTask), não uma tarefa executável — sem essa distinção, a limpeza genérica em
 *                     {@code ContinuationService} não saberia de qual coleção apagar a referência.
 */
public record AttachedEventReference(
        String instanceId,
        String definitionId,
        AttachedTaskType instanceType
) {
    /**
     * Compatibilidade com todo código anterior a {@code instanceType}: até a introdução de
     * {@code InterruptiveCatchEventDefinition}, todo boundary event de borda era um timer (sempre
     * {@code EXECUTABLE_TASK}).
     */
    public AttachedEventReference(String instanceId, String definitionId) {
        this(instanceId, definitionId, AttachedTaskType.EXECUTABLE_TASK);
    }
}