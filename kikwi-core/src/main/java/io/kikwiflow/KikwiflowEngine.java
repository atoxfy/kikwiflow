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

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.exception.ProcessInstanceNotFoundException;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.execution.ContinuationService;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.ProcessInstanceFactory;
import io.kikwiflow.execution.TaskAcquirer;
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
import io.kikwiflow.validation.DeployValidator;

import java.io.InputStream;
import java.math.BigDecimal;
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
        this.continuationService = new ContinuationService(kikwiEngineRepository, kikwiflowConfig);
    }

    public void start(){
        taskAcquirer.start();
    }

    public void stop(){
        taskAcquirer.stop();
    }

    public void claim(String externalTaskId, String assignee){
        this.kikwiEngineRepository.claim(externalTaskId, assignee);
    }

    public void unclaim(String externalTaskId){
        this.kikwiEngineRepository.unclaim(externalTaskId);
    }

    /**
     * Completa uma tarefa externa e continua a execução do processo.
     *
     * @param externalTaskId O ID da tarefa externa a ser completada.
     * @param tenantId O ID do tenant que está tentando completar a tarefa. A operação falhará se não corresponder ao tenant da instância.
     * @param variables Um mapa de variáveis a serem adicionadas ou atualizadas na instância do processo.
     * @return O estado final da instância do processo após a continuação da execução.
     * @throws TaskNotFoundException se nenhuma tarefa com o ID fornecido for encontrada.
     * @throws ProcessInstanceNotFoundException se a instância de processo associada não for encontrada.
     * @throws SecurityException se o tenantId fornecido não corresponder ao tenantId da instância do processo.
     */
    public ProcessInstance completeExternalTask(String externalTaskId, String tenantId, Map<String, ProcessVariable> variables, String targetFlowNodeId) {
        ExternalTask taskToComplete = kikwiEngineRepository.findExternalTaskById(externalTaskId)//CRIAR UM FIND AND LOCK
            .orElseThrow(() -> new TaskNotFoundException("ExternalTask not found with id: " + externalTaskId));

        if (!Objects.equals(taskToComplete.tenantId(), tenantId)) {
            throw new SecurityException(
                    "Tenant mismatch: Task " + externalTaskId + " does not belong to the provided tenant."
            );
        }

        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(taskToComplete.processInstanceId())
            .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + taskToComplete.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
            .orElseThrow(); 

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        if (variables != null) {
            processInstanceExecution.getVariables().putAll(variables);
        }

        FlowNodeDefinition completedNode = processDefinition.flowNodes().get(taskToComplete.taskDefinitionId());
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, variables, false, targetFlowNodeId);

        ExecutionResult executionResult;
        if (continuation != null && !continuation.nextNodes().isEmpty()) {
            FlowNodeDefinition startPoint = continuation.nextNodes().get(0); 
            executionResult = processExecutionManager.executeFlow(startPoint, processInstanceExecution, processDefinition, false, targetFlowNodeId);
        } else {
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), null);
        }

        return continuationService.handleContinuation(executionResult, taskToComplete);
    }

    public ProcessInstance executeFromTask(ExecutableTask executableTask){
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(executableTask.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + executableTask.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        FlowNodeDefinition flowNodeDefinition = processDefinition.flowNodes().get(executableTask.taskDefinitionId());
        ExecutionResult executionResult = processExecutionManager.executeFlow(flowNodeDefinition, processInstanceExecution, processDefinition, true, null);
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

    public ProcessInstance setVariables(String processInstanceId, Map<String, ProcessVariable> variables){
        return kikwiEngineRepository.addVariables(processInstanceId, variables);
    }


    public void clearDefinitionCache(){
        processDefinitionService.clearCache();
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
        private BigDecimal businessValue;
        private String tenantId;
        private String origin;

        private ProcessStarter(KikwiflowEngine engine) {
            this.engine = engine;
        }

        public ProcessStarter byKey(String key) {
            this.processDefinitionKey = key;
            return this;
        }

        public ProcessStarter onTenant(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public ProcessStarter withBusinessValue(BigDecimal businessValue) {
            this.businessValue = businessValue;
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

        public ProcessStarter from(String origin) {
            this.origin = origin;
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
            ProcessInstance processInstance = engine.kikwiEngineRepository.saveProcessInstance(ProcessInstanceFactory.create(businessKey, processDefinition.id(), variables, businessValue, tenantId, origin));
            ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstance);
            FlowNodeDefinition startPoint = processDefinition.defaultStartPoint();
            ExecutionResult executionResult = engine.processExecutionManager.executeFlow(startPoint, processInstanceExecution, processDefinition, false, null);

            return engine.continuationService.handleContinuation(executionResult);
        }
    }
}
