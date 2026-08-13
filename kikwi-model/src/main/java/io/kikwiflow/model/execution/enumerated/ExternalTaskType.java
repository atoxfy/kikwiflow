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
 * Discrimina o papel de uma {@code ExternalTask}. {@code null} (documentos persistidos antes deste campo
 * existir) é tratado como {@code STANDARD} por quem consome o valor — o mapper não faz esse fallback sozinho.
 */
public enum ExternalTaskType {
    STANDARD,
    EVENT_CATCHER_STANDALONE,
    EVENT_CATCHER_PARENT,
    EVENT_CATCHER_CHILD,
    /**
     * Espera de correlação anexada como boundary event interruptivo de um {@code EXECUTABLE_TASK}/{@code
     * EXTERNAL_TASK} (ver {@code InterruptiveCatchEventDefinition}) — sempre resolve 1 única chave e, ao ser
     * correlacionada, cancela o nó pai ({@code attachedToRefId}/{@code attachedToRefType}) exatamente como um
     * {@code BOUNDARY_INTERRUPTIVE_TIMER}.
     */
    BOUNDARY_INTERRUPTIVE_CATCH_EVENT
}
