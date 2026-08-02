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
import io.kikwiflow.execution.FailureHandler;
import io.kikwiflow.execution.FlowNodeExecutionFailure;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.ProcessInstanceFactory;
import io.kikwiflow.execution.TaskAcquirer;
import io.kikwiflow.execution.event.CriticalEventRecorder;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.execution.dto.ExecutionOutcome;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.lightweight.SyncContinuationFailed;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.util.KikwiflowBanner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class KikwiflowEngine {

    private final ProcessDefinitionService processDefinitionService;
    private final Navigator navigator;
    private final ProcessExecutionManager processExecutionManager;
    private final KikwiflowConfig kikwiflowConfig;
    private final AsynchronousEventPublisher asynchronousEventPublisher;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final TaskAcquirer taskAcquirer;
    private final ContinuationService continuationService;
    private final FailureHandler failureHandler;
    private final CriticalEventRecorder criticalEventRecorder;

    public KikwiflowEngine(
            ProcessDefinitionService processDefinitionService,
            Navigator navigator,
            ProcessExecutionManager processExecutionManager,
            KikwiEngineRepository kikwiEngineRepository,
            KikwiflowConfig kikwiflowConfig,
            AsynchronousEventPublisher asynchronousEventPublisher,
            ContinuationService continuationService,
            FailureHandler failureHandler,
            TaskAcquirer taskAcquirer,
            CriticalEventRecorder criticalEventRecorder) {

        this.processDefinitionService = processDefinitionService;
        this.navigator = navigator;
        this.processExecutionManager = processExecutionManager;
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.kikwiflowConfig = kikwiflowConfig;

        this.asynchronousEventPublisher = asynchronousEventPublisher;
        this.continuationService = continuationService;
        this.failureHandler = failureHandler;
        this.taskAcquirer = taskAcquirer;
        this.criticalEventRecorder = criticalEventRecorder;
    }

    public void start(){
        KikwiflowBanner.print();
        taskAcquirer.start(this);
    }

    public void stop(){
        taskAcquirer.stop();
    }

    public void deleteInstance(String processInstanceId, IdentityContext identityContext){
        this.kikwiEngineRepository.deleteProcessInstanceById(processInstanceId);
    }

    public void claim(String externalTaskId, String assignee, IdentityContext identityContext){
        List<OutboxEventEntity> events = new ArrayList<>();
        kikwiEngineRepository.findExternalTaskById(externalTaskId)
                .ifPresent(task -> criticalEventRecorder.recordExternalTaskClaimed(events, task, assignee, identityContext.actorId()));
        this.kikwiEngineRepository.claim(externalTaskId, assignee, events);
    }

    public void unclaim(String externalTaskId, IdentityContext identityContext){
        List<OutboxEventEntity> events = new ArrayList<>();
        kikwiEngineRepository.findExternalTaskById(externalTaskId)
                .ifPresent(task -> criticalEventRecorder.recordExternalTaskUnclaimed(events, task, identityContext.actorId()));
        this.kikwiEngineRepository.unclaim(externalTaskId, events);
    }


    /**
     * Retenta manualmente um incidente aberto, reativando a tarefa associada
     * e marcando o incidente como RESOLVED de forma atômica e transacional.
     */
    public void retryIncident(String incidentId, IdentityContext identityContext) {
        Incident incident = kikwiEngineRepository.findIncidentById(incidentId)
                .orElseThrow(() -> new TaskNotFoundException("Incident not found with id: " + incidentId));

        if (incident.status() != IncidentStatus.OPEN) {
            throw new IllegalStateException("Only OPEN incidents can be retried.");
        }

        ExecutableTask failedTask = kikwiEngineRepository.findExecutableTaskById(incident.executionId())
                .orElseThrow(() -> new TaskNotFoundException("Associated ExecutableTask not found: " + incident.executionId()));

        long retriesToRestore = failedTask.retryPolicy() != null
                ? failedTask.retryPolicy().maxRetries()
                : kikwiflowConfig.getDefaultMaxRetries();

        ExecutableTask restoredTask = failedTask.toBuilder()
                .status(ExecutableTaskStatus.PENDING)
                .retries(retriesToRestore)
                .error(null)
                .dueDate(Instant.now())
                .executorId(null)
                .build();


        Incident resolvedIncident = new Incident(
                incident.id(),
                incident.type(),
                incident.message(),
                incident.stackTrace(),
                incident.processDefinitionId(),
                incident.processInstanceId(),
                incident.executionId(),
                incident.createdAt(),
                IncidentStatus.RESOLVED,
                incident.taskDefinitionId()
        );

        String tenantId = kikwiEngineRepository.findProcessInstanceById(incident.processInstanceId())
                .map(ProcessInstance::tenantId)
                .orElse(null);

        List<OutboxEventEntity> events = new ArrayList<>();
        criticalEventRecorder.recordIncidentResolved(events, resolvedIncident, tenantId, identityContext.actorId());

        UnitOfWork uow = new UnitOfWork(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(restoredTask),
                null,
                events.isEmpty() ? null : events,
                null,
                List.of(resolvedIncident),
                null,
                null,
                null,
                null
        );

        kikwiEngineRepository.commitWork(uow);
    }

    /**
     * Permite a manipulação cirúrgica de uma tarefa em execução ou em falha.
     * Ideal para suporte operacional (hot-fixing) sem afetar a definição do processo.
     */
    public void overrideTaskRetryContext(String executableTaskId,
                                         Instant customDueDate,
                                         Long newRetriesCount,
                                         RetryPolicy customRetryPolicy,
                                         IdentityContext identityContext) {

        ExecutableTask task = kikwiEngineRepository.findExecutableTaskById(executableTaskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + executableTaskId));

        ExecutableTask.Builder builder = task.toBuilder();

        if (customDueDate != null) {
            builder.dueDate(customDueDate);
        }

        if (newRetriesCount != null) {
            builder.retries(newRetriesCount);
        }

        if (customRetryPolicy != null) {
            builder.retryPolicy(customRetryPolicy);
        }

        ExecutableTask overriddenTask = builder
                .status(ExecutableTaskStatus.PENDING)
                .error(null)
                .executorId(null)
                .build();

        UnitOfWork uow = new UnitOfWork(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(overriddenTask),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        kikwiEngineRepository.commitWork(uow);
    }

    /**
     * Completa uma tarefa externa e continua a execução do processo.
     *
     * @param externalTaskId O ID da tarefa externa a ser completada.
     * @param identityContext identityContext
     * @param variables Um mapa de variáveis a serem adicionadas ou atualizadas na instância do processo.
     * @return O estado final da instância do processo após a continuação da execução.
     * @throws TaskNotFoundException se nenhuma tarefa com o ID fornecido for encontrada.
     * @throws ProcessInstanceNotFoundException se a instância de processo associada não for encontrada.
     * @throws SecurityException se o tenantId fornecido não corresponder ao tenantId da instância do processo.
     */
    public ProcessInstance completeExternalTask(String externalTaskId, Map<String, ProcessVariable> variables, IdentityContext identityContext) {
        ExternalTask taskToComplete = kikwiEngineRepository.findExternalTaskById(externalTaskId)
                .orElseThrow(() -> new TaskNotFoundException("ExternalTask not found with id: " + externalTaskId));

        if (!Objects.equals(taskToComplete.tenantId(), identityContext.tenantId())) {
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
            processInstanceExecution.addVariables(variables);
        }

        FlowNodeDefinition completedNode = processDefinition.flowNodes().get(taskToComplete.taskDefinitionId());
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, processInstanceExecution.getVariables(), false);

        ExecutionResult executionResult;

        if (continuation != null && !continuation.nextNodes().isEmpty()) {

            FlowNodeDefinition startPoint = continuation.nextNodes().get(0);

            try {
                executionResult = processExecutionManager.executeFlow(
                        startPoint,
                        taskToComplete.branchId(),
                        taskToComplete.joinTaskId(),
                        processInstanceExecution,
                        processDefinition,
                        false
                );
            } catch (Exception ex) {

                Throwable rootCause = (ex instanceof FlowNodeExecutionFailure && ex.getCause() != null) ? ex.getCause() : ex;

                if(kikwiflowConfig.isStatsEnabled()){
                    SyncContinuationFailed telemetryEvent = new SyncContinuationFailed(
                            processInstanceExecution.getId(),
                            taskToComplete.id(),
                            startPoint.id(),
                            rootCause.getMessage(),
                            FailureHandler.getStackTrace(rootCause),
                            Instant.now()
                    );

                    this.asynchronousEventPublisher.publishEvent(telemetryEvent);
                }

                throw new RuntimeException("Kikwiflow Core: Falha síncrona no nó [" + startPoint.id() + "] após a conclusão da tarefa externa [" + taskToComplete.id() + "]. Transação abortada.", rootCause);
            }

        } else {
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), null);
        }

        return continuationService.handleContinuation(executionResult, taskToComplete, processDefinition, identityContext.actorId());
    }


    public ProcessInstance executeFromTask(ExecutableTask executableTask){
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(executableTask.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + executableTask.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        FlowNodeDefinition flowNodeDefinition = processDefinition.flowNodes().get(executableTask.taskDefinitionId());
        if (flowNodeDefinition == null) {
            throw new TaskNotFoundException("Definição de nó não encontrada na ProcessDefinition para a tarefa: " + executableTask.taskDefinitionId());
        }


        ExecutionResult executionResult;

        try {
            executionResult = processExecutionManager.executeFlow(
                    flowNodeDefinition,
                    executableTask.branchId(),
                    executableTask.joinTaskId(),
                    processInstanceExecution,
                    processDefinition,
                    true
            );

            return this.continuationService.handleContinuation(executionResult, executableTask, processDefinition);

        } catch (Exception e) {
            Exception rootException = e;
            List<OutboxEventEntity> pendingCriticalEvents = List.of();

            if (e instanceof FlowNodeExecutionFailure flowNodeExecutionFailure) {
                pendingCriticalEvents = flowNodeExecutionFailure.getCriticalEvents();
                if (flowNodeExecutionFailure.getCause() instanceof Exception causeAsException) {
                    rootException = causeAsException;
                }
            }

            System.err.println("Task execution failed: " + rootException.getMessage());
            failureHandler.handleFailure(executableTask, rootException, pendingCriticalEvents, processInstanceRecord.tenantId());
            return processInstanceRecord;
        }
    }

    private void registerListeners(List<ExecutionEventListener> executionEventListeners){
        if(Objects.nonNull(executionEventListeners)){
            executionEventListeners.forEach(asynchronousEventPublisher::registerListener);
        }
    }

    public ProcessInstance setVariables(String processInstanceId, Map<String, ProcessVariable> variables, IdentityContext identityContext){
        //TODO implement the identity context logic (authorization).
        List<OutboxEventEntity> events = new ArrayList<>();
        ProcessInstance processInstance = kikwiEngineRepository.findProcessInstanceById(processInstanceId).orElse(null);
        String processDefinitionId = processInstance != null ? processInstance.processDefinitionId() : null;
        String tenantId = processInstance != null ? processInstance.tenantId() : null;
        criticalEventRecorder.recordProcessVariableChanged(events, processInstanceId, processDefinitionId, tenantId, variables, identityContext.actorId());
        return kikwiEngineRepository.addVariables(processInstanceId, variables, events);
    }

    public ProcessInstance unsetVariables(String processInstanceId, Set<String> variableNames, IdentityContext identityContext){
        //TODO implement the identity context logic (authorization).
        List<OutboxEventEntity> events = new ArrayList<>();
        ProcessInstance processInstance = kikwiEngineRepository.findProcessInstanceById(processInstanceId).orElse(null);
        String processDefinitionId = processInstance != null ? processInstance.processDefinitionId() : null;
        String tenantId = processInstance != null ? processInstance.tenantId() : null;
        criticalEventRecorder.recordVariablesUnset(events, processInstanceId, processDefinitionId, tenantId, variableNames, identityContext.actorId());
        return kikwiEngineRepository.unsetVariables(processInstanceId, variableNames, events);
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
        private String actor;


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

        public ProcessStarter byActor(String actor) {
            this.actor = actor;
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

            ProcessInstance processInstance = ProcessInstanceFactory.create(businessKey, processDefinition.id(), variables, businessValue, tenantId, origin);
            ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstance);
            processInstanceExecution.setPersisted(false);

            String defaultStartPointId  = processDefinition.defaultStartPoint();
            FlowNodeDefinition defaultStartPoint = processDefinition.flowNodes().get(defaultStartPointId);
            Objects.requireNonNull(defaultStartPoint, "Malformed process definition: unknown default start point. The default start point needs to be declared in flow nodes map");

            ExecutionResult executionResult;
            try {
                executionResult = engine.processExecutionManager.executeFlow(
                        defaultStartPoint,
                        null,
                        null,
                        processInstanceExecution,
                        processDefinition,
                        false);
            } catch (FlowNodeExecutionFailure flowNodeExecutionFailure) {
                // Nada foi persistido ainda neste ponto (instância síncrona ainda não confirmada), então os
                // outbox events acumulados não têm onde ser gravados; propaga a causa original para preservar
                // o tipo/mensagem da exceção de negócio para quem chamou startProcess().execute().
                if (flowNodeExecutionFailure.getCause() instanceof RuntimeException runtimeCause) {
                    throw runtimeCause;
                }
                throw flowNodeExecutionFailure;
            }

            return engine.continuationService.handleContinuation(executionResult, processDefinition, actor);
        }
    }
}
