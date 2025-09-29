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
package io.kikwiflow.execution;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.event.FlowNodeExecuted;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.navigation.Navigator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orquestra a execução síncrona de um fluxo de processo.
 * <p>
 * Esta classe contém o loop de execução principal do motor. A sua responsabilidade é
 * receber um ponto de partida e conduzir a instância do processo através dos nós
 * sequenciais, utilizando o {@link Navigator} para determinar o próximo passo e o
 * {@link FlowNodeExecutor} para executar a lógica de cada nó.
 * <p>
 * A execução continua até que uma das seguintes condições de paragem seja encontrada:
 * <ul>
 *     <li>Um nó que é um estado de espera ({@link WaitState}).</li>
 *     <li>Um nó que exige um commit transacional antes da sua execução (commit-before).</li>
 *     <li>O fim do fluxo do processo (nenhum nó de saída).</li>
 * </ul>
 */
public class ProcessExecutionManager {

    private final FlowNodeExecutor flowNodeExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;

    /**
     * Constrói uma nova instância do gestor de execução.
     *
     * @param flowNodeExecutor O executor responsável pela lógica de um único nó.
     * @param navigator O componente que determina o próximo passo no fluxo.
     * @param kikwiflowConfig A configuração do motor, usada para verificar se funcionalidades como estatísticas estão ativadas.
     */
    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig) {
        this.flowNodeExecutor = flowNodeExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    /**
     * Executa um segmento de fluxo de processo a partir de um ponto de partida.
     * <p>
     * Este método invoca o {@link FlowNodeExecutor} para executar o fluxo de forma síncrona
     * até que um ponto de paragem (wait state, commit boundary, ou fim do processo) seja encontrado.
     *
     * @param startPoint O nó a partir do qual a execução deve começar.
     * @param processInstance A instância de processo em execução (mutável durante a execução).
     * @param processDefinition A definição do processo correspondente.
     * @return O {@link ExecutionResult} que contém o resultado da execução síncrona.
     */
    public ExecutionResult executeFlow(FlowNodeDefinition startPoint, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition, boolean isResumingFromAsyncBefore) {
        FlowNodeDefinition currentNode = startPoint;
        List<OutboxEventEntity> criticalEvents = new ArrayList<>();
        boolean isFirstNodeInLoop = true;

        while (currentNode != null){

            final boolean shouldStopForCommitBefore = isCommitBefore(currentNode) && !(isFirstNodeInLoop && isResumingFromAsyncBefore);

            if(isWaitState(currentNode) || shouldStopForCommitBefore){
                return new ExecutionResult(
                        new ExecutionOutcome(processInstance, criticalEvents),
                        new Continuation(List.of(currentNode), true)
                );
            }

            Instant startedAt = Instant.now();
            NodeExecutionStatus status;

            try {
                flowNodeExecutor.execute(processInstance, processDefinition, currentNode);
                status = NodeExecutionStatus.SUCCESS;
            } catch (Exception e) {
                status = NodeExecutionStatus.ERROR;
                // TODO: error handling, maybe create a failed event and stop.
                throw e;
            }

            if (kikwiflowConfig.isStatsEnabled() || kikwiflowConfig.isOutboxEventsEnabled()) {
                final FlowNodeExecutionSnapshot snapshot = FlowNodeExecutionSnapshot.builder()
                    .flowNodeDefinition(currentNode)
                    .processDefinitionSnapshot(processDefinition)
                    .processInstanceSnapshot(ProcessInstanceMapper.mapToRecord(processInstance))
                    .startedAt(startedAt)
                    .finishedAt(Instant.now())
                    .nodeExecutionStatus(status)
                    .build();

                FlowNodeExecuted flowNodeExecuted = FlowNodeExecuted.builder()
                    .flowNodeDefinitionId(snapshot.flowNodeDefinition().id())
                    .processInstanceId(snapshot.processInstance().id())
                    .processDefinitionId(snapshot.processDefinition().id())
                    .nodeExecutionStatus(snapshot.nodeExecutionStatus())
                    .startedAt(snapshot.startedAt())
                    .finishedAt(snapshot.finishedAt())
                    .build();

                criticalEvents.add(new OutboxEventEntity("FLOW_NODE_EXECUTED", flowNodeExecuted));
            }

            boolean isCommitAfter = Boolean.TRUE.equals(currentNode.commitAfter());
            Continuation continuation = navigator.determineNextContinuation(currentNode, processDefinition, processInstance.getVariables(), isCommitAfter, null);
            isFirstNodeInLoop = false;

            if (continuation == null || continuation.isAsynchronous()) {
                return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), continuation);
            } else {
                // TODO: Handle parallel gateways in the future
                currentNode = continuation.nextNodes().get(0);
            }
        }
        return new ExecutionResult(new ExecutionOutcome(processInstance, criticalEvents), null);
    }

    /**
     * Verifica se um nó é um "estado de espera" (wait state).
     * Um estado de espera interrompe a execução síncrona do motor, aguardando um gatilho externo.
     *
     * @param flowNodeDefinition O nó a ser verificado.
     * @return {@code true} se o nó implementa a interface {@link WaitState}.
     */
    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition) {
        return flowNodeDefinition instanceof WaitState;
    }

    /**
     * Verifica se um nó está configurado para forçar um commit transacional *antes* da sua execução.
     * @param flowNodeDefinition O nó a ser verificado.
     * @return {@code true} se o atributo `commitBefore` for verdadeiro, {@code false} caso contrário.
     */
    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore());
    }
}
