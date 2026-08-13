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

/**
 * Como um {@code CALL_ACTIVITY_COORDINATOR} com {@code collectionVariable} dispara as N iniciadoras
 * ({@code CALL_ACTIVITY_STARTER}) de seus filhos. {@code null} em {@code CallActivityDefinition.iterationMode()}
 * é tratado como {@code PARALLEL} em todo o motor — compatibilidade retroativa com processos implantados antes
 * deste campo existir, mesmo precedente já usado para {@code AttachedEventReference.instanceType}.
 */
public enum CallActivityIterationMode {
    /**
     * Todas as N iniciadoras são criadas de uma vez, na mesma transação do fan-out — os filhos disparam em
     * paralelo. Comportamento histórico/único do motor antes deste enum existir.
     */
    PARALLEL,
    /**
     * Só a iniciadora do elemento 0 é criada no fan-out; cada iniciadora seguinte só é criada depois que o
     * filho da anterior completa — um filho por vez. Ver docs/engine/20-subprocessos-call-activity-especificacao.md.
     */
    SEQUENTIAL
}
