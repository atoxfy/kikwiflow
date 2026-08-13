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

package io.kikwiflow.persistence.api.data;


import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;

import java.util.List;
import java.util.Map;

public record UnitOfWork(
        ProcessInstance instanceToCreate,
        ProcessInstance instanceToUpdate,
        ProcessInstance instanceToDelete,
        List<ExecutableTask> executableTasksToCreate,
        List<ExternalTask> externalTasksToCreate,
        List<String> executableTasksToDelete,
        List<ExecutableTask> executableTasksToUpdate,
        List<String> externalTasksToDelete,
        List<OutboxEventEntity> events,
        List<Incident> incidentsToCreate,
        List<Incident> incidentsToUpdate,
        List<String> incidentsToResolve,
        List<String> finishedNodeDefinitions,
        List<BranchPullIntention> branchPullIntentions,
        Map<String, VariableOperation> variableOperations,
        /**
         * ID do nó (pai ou o próprio nó) cuja finalização este commit representa, quando esse nó tem — ou é —
         * um evento de borda interruptivo. {@code null} para a esmagadora maioria dos commits (nós sem
         * boundary event algum), preservando o caminho rápido de hoje sem custo extra.
         *
         * <p>{@code commitWork} trata isto como um guard obrigatório: apaga esta linha condicionalmente antes
         * de qualquer outra escrita e aborta a transação inteira com {@code OptimisticLockingFailureException}
         * se ela já não existir mais — cobre tanto "pai concluiu normalmente enquanto um boundary disparava"
         * quanto "dois boundary events interruptivos (timer + catch event) dispararam ao mesmo tempo": só quem
         * primeiro apagar esta linha específica pode prosseguir com a continuação; o perdedor não escreve nada.
         */
        String finalizingNodeId,
        AttachedTaskType finalizingNodeType
        ) {

    /**
     * Compatibilidade com todo código anterior ao guard de finalização: equivalente a passar
     * {@code finalizingNodeId=null}, ou seja, nenhum guard é aplicado — o mesmo comportamento de hoje.
     */
    public UnitOfWork(
            ProcessInstance instanceToCreate,
            ProcessInstance instanceToUpdate,
            ProcessInstance instanceToDelete,
            List<ExecutableTask> executableTasksToCreate,
            List<ExternalTask> externalTasksToCreate,
            List<String> executableTasksToDelete,
            List<ExecutableTask> executableTasksToUpdate,
            List<String> externalTasksToDelete,
            List<OutboxEventEntity> events,
            List<Incident> incidentsToCreate,
            List<Incident> incidentsToUpdate,
            List<String> incidentsToResolve,
            List<String> finishedNodeDefinitions,
            List<BranchPullIntention> branchPullIntentions,
            Map<String, VariableOperation> variableOperations
    ) {
        this(instanceToCreate, instanceToUpdate, instanceToDelete, executableTasksToCreate, externalTasksToCreate,
                executableTasksToDelete, executableTasksToUpdate, externalTasksToDelete, events, incidentsToCreate,
                incidentsToUpdate, incidentsToResolve, finishedNodeDefinitions, branchPullIntentions,
                variableOperations, null, null);
    }
}