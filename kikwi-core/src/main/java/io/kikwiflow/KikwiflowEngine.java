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
package io.kikwiflow;

import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.exception.ProcessInstanceNotFoundException;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.execution.*;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.Executable;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KikwiflowEngine {

    private final ExecutorService executorService;
    private final ProcessDefinitionService processDefinitionService;
    private final Navigator navigator;
    private final ProcessExecutionManager processExecutionManager;
    private final KikwiflowConfig kikwiflowConfig;
    private final List<ExecutionEventListener> eventListeners;
    private final AsynchronousEventPublisher asynchronousEventPublisher;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final TaskAcquirer taskAcquirer;

    public KikwiflowEngine(KikwiEngineRepository kikwiEngineRepository, KikwiflowConfig kikwiflowConfig, DelegateResolver delegateResolver, List<ExecutionEventListener> executionEventListeners){
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.asynchronousEventPublisher = new AsynchronousEventPublisher(executorService);
        this.registerListeners(executionEventListeners);
        final BpmnParser bpmnParser = new DefaultBpmnParser();
        this.processDefinitionService = new ProcessDefinitionService(bpmnParser, kikwiEngineRepository);
        this.navigator = new Navigator(processDefinitionService);
        this.processExecutionManager = new ProcessExecutionManager(new FlowNodeExecutor(new TaskExecutor(delegateResolver)), navigator, kikwiflowConfig);
        this.kikwiflowConfig = kikwiflowConfig;
        this.eventListeners = executionEventListeners;
        this.taskAcquirer = new TaskAcquirer(this, kikwiEngineRepository, kikwiflowConfig );
    }


    public void start(){
        taskAcquirer.start();
    }

    public void stop(){
        taskAcquirer.stop();
    }

    /**
     * Completa uma tarefa externa e continua a execução do processo.
     *
     * @param externalTaskId O ID da tarefa externa a ser completada.
     * @param variables Um mapa de variáveis a serem adicionadas ou atualizadas na instância do processo.
     * @return O estado final da instância do processo após a continuação da execução.
     * @throws TaskNotFoundException se nenhuma tarefa com o ID fornecido for encontrada.
     * @throws ProcessInstanceNotFoundException se a instância de processo associada não for encontrada.
     */
    public ProcessInstance completeExternalTask(String externalTaskId, Map<String, ProcessVariable> variables) {
        // 1. Encontrar a tarefa a ser completada.
        ExternalTask taskToComplete = kikwiEngineRepository.findExternalTaskById(externalTaskId)
            .orElseThrow(() -> new TaskNotFoundException("ExternalTask not found with id: " + externalTaskId));

        // 2. Obter os dados necessários para a continuação.
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(taskToComplete.processInstanceId())
            .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + taskToComplete.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
            .orElseThrow(); // Se a instância existe, a definição também deve existir.

        // 3. Preparar o estado de execução.
        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        if (variables != null) {
            processInstanceExecution.getVariables().putAll(variables);
        }

        // 4. Navegar: encontrar o nó que foi completado e determinar o próximo passo.
        FlowNodeDefinition completedNode = processDefinition.flowNodes().get(taskToComplete.taskDefinitionId());
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, false);

        // 5. Executar: se houver um próximo passo, delegar ao executor.
        ExecutionResult executionResult;
        if (continuation != null && !continuation.nextNodes().isEmpty()) {
            FlowNodeDefinition startPoint = continuation.nextNodes().get(0); // Simplificado para fluxo linear
            executionResult = processExecutionManager.executeFlow(startPoint, processInstanceExecution, processDefinition, false);
        } else {
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), null);
        }

        // 6. Persistir o resultado e o estado da tarefa completada.
        return handleContinuation(executionResult, taskToComplete.id(), null);
    }

    public ProcessInstance executeFromTask(ExecutableTask executableTask){
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(executableTask.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + executableTask.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        FlowNodeDefinition flowNodeDefinition = processDefinition.flowNodes().get(executableTask.taskDefinitionId());

        ExecutionResult executionResult = processExecutionManager.executeFlow(flowNodeDefinition, processInstanceExecution, processDefinition, true);
        return handleContinuation(executionResult, null, executableTask.id());
    }

    private void registerListeners(List<ExecutionEventListener> executionEventListeners){
        if(Objects.nonNull(executionEventListeners)){
            executionEventListeners.forEach(asynchronousEventPublisher::registerListener);
        }
    }

    public ProcessDefinition deployDefinition(InputStream is) throws Exception {
        return processDefinitionService.deploy(is);
    }

    private ProcessInstance handleContinuation(ExecutionResult executionResult){
        return handleContinuation(executionResult, null, null);
    }

    /**
     * Orquestra a persistência do estado final de uma execução síncrona.
     * Este método constrói e comita a {@link UnitOfWork}.
     */
    private ProcessInstance handleContinuation(ExecutionResult executionResult, String completedExternalTaskId,
                                               String completedExecutableTaskId) {
        Continuation continuation = executionResult.continuation();
        ExecutionOutcome executionOutcome = executionResult.outcome();
        ProcessInstanceExecution processInstance = executionOutcome.processInstance();

        // O processo parou em uma tarefa assíncrona (ou estado de espera).
        List<ExecutableTask> nextExecutableTasks = new ArrayList<>();
        List<ExternalTask>  nextExternalTasks = new ArrayList<>();
        if (continuation != null && continuation.isAsynchronous()) {
            //TODO classificar entre external e executable
            continuation.nextNodes().forEach(flowNodeDefinitionSnapshot -> {
                if(flowNodeDefinitionSnapshot instanceof WaitState){
                    nextExternalTasks.add(ExternalTask.builder()
                            .processDefinitionId(processInstance.getProcessDefinitionId())
                            .taskDefinitionId(flowNodeDefinitionSnapshot.id())
                            .processInstanceId(processInstance.getId())
                            .name(flowNodeDefinitionSnapshot.name())
                            .description(flowNodeDefinitionSnapshot.description())
                            .build());

                }else if (flowNodeDefinitionSnapshot instanceof Executable){
                    nextExecutableTasks.add(ExecutableTask.builder()
                            .processDefinitionId(processInstance.getProcessDefinitionId())
                            .taskDefinitionId(flowNodeDefinitionSnapshot.id())
                            .processInstanceId(processInstance.getId())
                            .build());
                }
            });
        }else{
            processInstance.setEndedAt(Instant.now());
            processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
        }


        List<OutboxEventEntity> events = new ArrayList<>(executionOutcome.events());
        if(ProcessInstanceStatus.COMPLETED.equals(processInstance.getStatus())){
           ProcessInstanceFinished processInstanceFinished = ProcessInstanceFinished.builder()
                    .processDefinitionId(processInstance.getProcessDefinitionId())
                    .businessKey(processInstance.getBusinessKey())
                    .id(processInstance.getId())
                    .status(processInstance.getStatus())
                    .variables(processInstance.getVariables())
                    .startedAt(processInstance.getStartedAt())
                    .endedAt(processInstance.getEndedAt())
                    .build();


            events.add(new OutboxEventEntity(processInstanceFinished));
        }

        ProcessInstance processInstanceToSave = ProcessInstanceMapper.mapToRecord(processInstance);

        List<String> executableTasksToDelete = new ArrayList<>();
        if (completedExecutableTaskId!= null) {
            executableTasksToDelete.add(completedExecutableTaskId);
        }

        List<String> externalTasksToDelete = new ArrayList<>();
        if (completedExternalTaskId != null) {
            externalTasksToDelete.add(completedExternalTaskId);
        }

        UnitOfWork updatedUnitOfWork = new UnitOfWork(
                !ProcessInstanceStatus.COMPLETED.equals(processInstanceToSave.status()) ? processInstanceToSave : null,
                ProcessInstanceStatus.COMPLETED.equals(processInstanceToSave.status()) ? processInstanceToSave : null,
                nextExecutableTasks,
                nextExternalTasks,
                executableTasksToDelete,
                externalTasksToDelete,
                events
        );

        kikwiEngineRepository.commitWork(updatedUnitOfWork);
        return processInstanceToSave;
    }

    /**
     * Inicia a construção de uma nova instância de processo de forma fluente.
     *
     * @return Um builder {@link ProcessStarter} para configurar e executar a instância.
     */
    public ProcessStarter startProcess() {
        return new ProcessStarter(this);
    }

    /**
     * Builder para configurar e iniciar uma nova instância de processo.
     * Permite uma API fluente para definir os parâmetros de inicialização.
     */
    public class ProcessStarter {
        private final KikwiflowEngine engine;
        private String processDefinitionKey;
        private String businessKey;
        private Map<String, ProcessVariable> variables = new HashMap<>();

        private ProcessStarter(KikwiflowEngine engine) {
            this.engine = engine;
        }

        public ProcessStarter byKey(String key) {
            this.processDefinitionKey = key;
            return this;
        }

        public ProcessStarter withBusinessKey(String key) {
            this.businessKey = key;
            return this;
        }

        public ProcessStarter withVariables(Map<String, ProcessVariable> vars) {
            if (vars != null) {
                this.variables = new HashMap<>(vars);
            }
            return this;
        }

        /**
         * Executa a inicialização do processo com os parâmetros fornecidos.
         *
         * @return Um snapshot do estado da instância do processo após a execução inicial.
         */
        public ProcessInstance execute() {
            Objects.requireNonNull(processDefinitionKey, "Process definition key cannot be null. Use byKey().");
            Objects.requireNonNull(businessKey, "Business key cannot be null. Use withBusinessKey().");

            ProcessDefinition processDefinition = engine.processDefinitionService.getByKeyOrElseThrow(processDefinitionKey);
            ProcessInstanceExecution processInstanceExecution = ProcessInstanceExecutionFactory.create(businessKey, processDefinition.id(), variables);
            ProcessInstance processInstance = engine.kikwiEngineRepository.saveProcessInstance(ProcessInstanceMapper.mapToRecord(processInstanceExecution));
            processInstanceExecution.setId(processInstance.id());

            FlowNodeDefinition startPoint = processDefinition.defaultStartPoint();
            ExecutionResult executionResult = engine.processExecutionManager.executeFlow(startPoint, processInstanceExecution, processDefinition, false);

            return engine.handleContinuation(executionResult);
        }
    }
}
