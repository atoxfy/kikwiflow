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

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.exception.ProcessInstanceNotFoundException;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.execution.*;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.io.InputStream;
import java.util.*;
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
    private final ContinuationService continuationService;

    public KikwiflowEngine(ProcessDefinitionService processDefinitionService, Navigator navigator, ProcessExecutionManager processExecutionManager, KikwiEngineRepository kikwiEngineRepository, KikwiflowConfig kikwiflowConfig, List<ExecutionEventListener> executionEventListeners){
        this.processDefinitionService = processDefinitionService;
        this.navigator = navigator;
        this.processExecutionManager = processExecutionManager;
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.asynchronousEventPublisher = new AsynchronousEventPublisher(executorService);
        this.registerListeners(executionEventListeners);
        this.kikwiflowConfig = kikwiflowConfig;
        this.eventListeners = executionEventListeners;
        this.taskAcquirer = new TaskAcquirer(this, kikwiEngineRepository, kikwiflowConfig );
        this.continuationService = new ContinuationService(kikwiEngineRepository);
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
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, variables, false);

        // 5. Executar: se houver um próximo passo, delegar ao executor.
        ExecutionResult executionResult;
        if (continuation != null && !continuation.nextNodes().isEmpty()) {
            FlowNodeDefinition startPoint = continuation.nextNodes().get(0); // Simplificado para fluxo linear
            executionResult = processExecutionManager.executeFlow(startPoint, processInstanceExecution, processDefinition, false);
        } else {
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), null);
        }

        // 6. Persistir o resultado e o estado da tarefa completada.
        return continuationService.handleContinuation(executionResult, taskToComplete);
    }

    public ProcessInstance executeFromTask(ExecutableTask executableTask){
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(executableTask.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + executableTask.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        FlowNodeDefinition flowNodeDefinition = processDefinition.flowNodes().get(executableTask.taskDefinitionId());
        ExecutionResult executionResult = processExecutionManager.executeFlow(flowNodeDefinition, processInstanceExecution, processDefinition, true);
        return this.continuationService.handleContinuation(executionResult, executableTask);
    }

    private void registerListeners(List<ExecutionEventListener> executionEventListeners){
        if(Objects.nonNull(executionEventListeners)){
            executionEventListeners.forEach(asynchronousEventPublisher::registerListener);
        }
    }

    public ProcessDefinition deployDefinition(InputStream is) throws Exception {
        return processDefinitionService.deploy(is);
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

    private ProcessInstance handleContinuation(ExecutionResult executionResult){
        return this.continuationService.handleContinuation(executionResult);
    }
}
