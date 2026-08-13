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

package io.kikwiflow.model.execution.enumerated;

public enum ExternalTaskStatus {
    CREATED,
    /**
     * Exclusivo de tarefas-filhas de um EVENT_CATCHER em modo GROUP: a chave já foi correlacionada, mas a
     * linha é mantida (em vez de apagada) até a tarefa-mãe concluir/ser interrompida — permite ao Monitor
     * mostrar displayName/correlationKey de itens já concluídos sem precisar de nenhum campo de snapshot
     * separado. A limpeza acontece via a cascata por coordinatorTaskId em commitWork, junto com a mãe.
     */
    CORRELATED,
    COMPLETED
}
