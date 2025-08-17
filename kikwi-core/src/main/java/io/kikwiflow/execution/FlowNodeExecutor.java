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

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.model.event.FlowNodeExecuted;
import io.kikwiflow.execution.dto.FlowNodeExecutionResult;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.node.Executable;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.model.event.OutboxEventEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * O coração da execução do motor Kikwiflow.
 * <p>
 * Esta classe é responsável por conduzir uma instância de processo através do seu fluxo,
 * executando os nós ({@link FlowNodeDefinition}) de forma sequencial. A sua principal
 * função é operar num loop que avança no processo até encontrar um "ponto de paragem".
 * <p>
 * Um ponto de paragem pode ser:
 * <ul>
 *     <li>Um <strong>estado de espera (Wait State)</strong>, como uma {@link io.kikwiflow.model.execution.node.HumanTask}.</li>
 *     <li>Um nó que exige um commit transacional antes da sua execução (commit-before).</li>
 *     <li>O fim do processo (nenhum nó seguinte).</li>
 * </ul>
 * O resultado da sua execução é um {@link ExecutionResult}, que encapsula o estado
 * final da instância e o plano de continuação.
 */
public class FlowNodeExecutor {

    private final TaskExecutor taskExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;

    /**
     * Constrói uma nova instância do FlowNodeExecutor.
     *
     * @param taskExecutor O executor responsável por invocar a lógica de negócio de uma tarefa (ex: {@link JavaDelegate}).
     * @param navigator O componente que determina o próximo passo no fluxo do processo.
     * @param kikwiflowConfig A configuração do motor, usada para verificar se funcionalidades como estatísticas estão ativadas.
     */
    public FlowNodeExecutor(TaskExecutor taskExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig) {
        this.taskExecutor = taskExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    /**
     * Classe interna privada que encapsula a execução de um único nó de fluxo.
     * <p>
     * Atua como um builder e executor para um passo, mantendo o estado (tempos de início/fim, status)
     * e orquestrando a chamada à lógica de negócio e a determinação da continuação.
     */
    private class FlowNodeExecution{
        private FlowNodeDefinition flowNodeDefinition;
        private ProcessDefinition processDefinition;
        private ProcessInstanceExecution processInstance;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;
        private Continuation continuation;
        private Supplier<Exception> errorSupplier;

        public FlowNodeExecution(){
        }

        public FlowNodeExecution flowNode(FlowNodeDefinition flowNodeDefinition){
            this.flowNodeDefinition = flowNodeDefinition;
            return this;
        }

        public FlowNodeExecution processInstance(ProcessInstanceExecution processInstance){
            this.processInstance = processInstance;
            return this;
        }

        public FlowNodeExecution processDefinition(ProcessDefinition processDefinition){
            this.processDefinition = processDefinition;
            return this;
        }

        public FlowNodeExecution onError(Supplier<Exception> supplier){
            this.errorSupplier = supplier;
            return this;
        }

        /**
         * Executa a lógica para um único nó de fluxo.
         * <p>
         * Envolve a execução da tarefa, tratamento de erros e a criação de um snapshot de execução para estatísticas.
         * @return O resultado da execução do nó, contendo o snapshot e a continuação.
         */
        public FlowNodeExecutionResult execute(){

            try{
                this.startedAt = Instant.now();
                this.continuation = executeAndGetContinuation(flowNodeDefinition, processInstance, processDefinition);
                this.nodeExecutionStatus = NodeExecutionStatus.SUCCESS;
            }catch (Exception e){
                this.nodeExecutionStatus = NodeExecutionStatus.ERROR;
                if(Objects.nonNull(this.errorSupplier)){
                    this.errorSupplier.get();
                }

                throw e;
            }

            this.finishedAt = Instant.now();

            //todo
            final FlowNodeExecutionSnapshot flowNodeExecutionSnapshot = kikwiflowConfig.isStatsEnabled()
                    || kikwiflowConfig.isOutboxEventsEnabled() ? FlowNodeExecutionSnapshot.builder()
                    .flowNodeDefinition(flowNodeDefinition)
                    .processDefinitionSnapshot(processDefinition)
                    .processInstanceSnapshot(ProcessInstanceMapper.mapToRecord(processInstance))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .nodeExecutionStatus(nodeExecutionStatus)
                    .build() : null;

            return new FlowNodeExecutionResult(flowNodeExecutionSnapshot, continuation);
        }
    }

    /**
     * Executa o processo em um loop contínuo a partir de um ponto de início,
     * até que um ponto de paragem seja encontrado.
     * <p>
     * Este é o método central do executor. Ele itera sobre os nós do fluxo,
     * executando cada um e navegando para o próximo, até que uma das seguintes
     * condições seja satisfeita:
     * <ol>
     *   <li>O processo chega ao fim (não há mais nós para executar).</li>
     *   <li>Um nó é um estado de espera ({@link #isWaitState(FlowNodeDefinition)}).</li>
     *   <li>Um nó está marcado com `commitBefore=true` ({@link #isCommitBefore(FlowNodeDefinition)}).</li>
     * </ol>
     *
     * @param startPoint O nó a partir do qual a execução deve começar.
     * @param processInstance A instância de processo em execução (mutável durante a execução).
     * @param processDefinition A definição do processo correspondente.
     * @return Um {@link ExecutionResult} contendo o resultado da execução ({@link ExecutionOutcome})
     *         e o plano de continuação ({@link Continuation}). Retorna {@code null} se o processo
     *         terminar naturalmente sem um estado de espera explícito no final.
     */
    public ExecutionResult runWhileNotFindAStopPoint(FlowNodeDefinition startPoint, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition){
        FlowNodeDefinition currentNode = startPoint;

        List<OutboxEventEntity> criticalEvents = new ArrayList<>();

        while (currentNode != null && !isWaitState(currentNode) && !isCommitBefore(currentNode)) {
            FlowNodeExecutionResult flowNodeExecutionResult = new FlowNodeExecution()
                    .processInstance(processInstance)
                    .flowNode(currentNode)
                    .processDefinition(processDefinition)
                    .execute();

            Continuation continuation = flowNodeExecutionResult.continuation();

            if(kikwiflowConfig.isStatsEnabled()){
                FlowNodeExecutionSnapshot flowNodeExecutionSnapshot = flowNodeExecutionResult.flowNodeExecutionSnapshot();
                FlowNodeExecuted flowNodeExecuted = new FlowNodeExecuted();
                flowNodeExecuted.setFlowNodeDefinitionId(flowNodeExecutionSnapshot.flowNodeDefinition().id());
                flowNodeExecuted.setProcessInstanceId(flowNodeExecutionSnapshot.processInstance().id());
                flowNodeExecuted.setProcessDefinitionId(flowNodeExecutionSnapshot.processDefinition().id());
                flowNodeExecuted.setNodeExecutionStatus(flowNodeExecutionSnapshot.nodeExecutionStatus());
                flowNodeExecuted.setStartedAt(flowNodeExecutionSnapshot.startedAt());
                flowNodeExecuted.setFinishedAt(flowNodeExecutionSnapshot.finishedAt());
                OutboxEventEntity outboxEvent = new OutboxEventEntity(flowNodeExecuted);
                criticalEvents.add(outboxEvent);
            }


            if (continuation == null || continuation.isAsynchronous()) {
                return new ExecutionResult( new ExecutionOutcome(processInstance,
                        criticalEvents),
                        continuation);

            } else {
                //todo aqui devemos ver futuramente para os casos de processamento paralelo
                currentNode = continuation.nextNodes().get(0);
            }
        }


        // Se saímos do loop, ou o processo terminou (currentNode == null) ou
        // encontrámos um ponto de paragem (wait state ou commit before).
        if (currentNode != null) {
            return new ExecutionResult(
                    new ExecutionOutcome(processInstance,
                            criticalEvents),
                    new Continuation(List.of(currentNode), true));
        }

        return null;
    }

    /**
     * (Reservado para uso futuro) Persiste o estado da execução.
     * @param unitOfWork A unidade de trabalho contendo as alterações a serem persistidas.
     */
    private void commitExecutionPath(UnitOfWork unitOfWork){
      //  processInstanceManager.update(unitOfWork.instanceToUpdate());
        if(kikwiflowConfig.isStatsEnabled()){
          //  asynchronousEventPublisher.publishEvents(lightweightEvents);
        }
    }

    /**
     * Executa a lógica de negócio associada a um nó, se aplicável.
     * <p>
     * Delega a execução para o {@link TaskExecutor} se o nó for do tipo {@link Executable}.
     * Para outros tipos de nós (como eventos), este método não faz nada.
     *
     * @param executionContext O contexto de execução atual, contendo a instância, definição e o nó.
     */
    private void execute(ExecutionContext executionContext) {
        FlowNodeDefinition flowNodeDefinition = executionContext.getFlowNode();
        if(flowNodeDefinition instanceof Executable){
            taskExecutor.execute(executionContext);
        }
    }

    /**
     * Orquestra a execução de um único nó e determina a próxima continuação.
     *
     * @param flowNodeDefinition O nó a ser executado.
     * @param processInstance A instância de processo.
     * @param processDefinition A definição do processo.
     * @return A {@link Continuation} para o próximo passo.
     */
    private Continuation executeAndGetContinuation(FlowNodeDefinition flowNodeDefinition, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition){
        ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNodeDefinition);
        execute(executionContext);
        boolean isCommitAfter = isCommitAfter(flowNodeDefinition);
        return navigator.determineNextContinuation(flowNodeDefinition, processDefinition, isCommitAfter);
    }

    /**
     * Verifica se um nó está configurado para forçar um commit transacional *após* a sua execução.
     *
     * @param flowNodeDefinition O nó a ser verificado.
     * @return {@code true} se o atributo `commitAfter` for verdadeiro, {@code false} caso contrário.
     */
    private boolean isCommitAfter(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitAfter());
    }

    /**
     * Verifica se um nó é um "estado de espera" (wait state).
     * <p>
     * Um estado de espera interrompe a execução síncrona do motor, aguardando um gatilho externo.
     *
     * @param flowNodeDefinition O nó a ser verificado.
     * @return {@code true} se o nó implementa a interface {@link WaitState}.
     */
    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition){
        return flowNodeDefinition instanceof WaitState;
    }

    /**
     * Verifica se um nó está configurado para forçar um commit transacional *antes* da sua execução.
     *
     * @param flowNodeDefinition O nó a ser verificado.
     * @return {@code true} se o atributo `commitBefore` for verdadeiro, {@code false} caso contrário.
     */
    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition){
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore());
    }
}
