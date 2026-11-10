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
import io.kikwiflow.exception.ExternalTaskLockException;
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
import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.lightweight.SyncContinuationFailed;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.AgentActivityReport;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.util.KikwiflowBanner;
import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.variable.VariableContractValidator;
import io.kikwiflow.variable.VariableValidationError;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    // Sem estado relevante nem dependências: instanciado aqui para não alterar o construtor/wiring do motor.
    private final VariableContractValidator variableContractValidator = new VariableContractValidator();

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

        Optional<ExecutableTask> failedExecutable = kikwiEngineRepository.findExecutableTaskById(incident.executionId());
        if (failedExecutable.isEmpty()) {
            // Incidente aberto por um worker com lease (docs/engine/31, E2): a tarefa externa volta a ser adquirível.
            ExternalTask failedExternal = kikwiEngineRepository.findExternalTaskById(incident.executionId())
                    .orElseThrow(() -> new TaskNotFoundException("Associated task not found: " + incident.executionId()));
            retryExternalTaskIncident(incident, failedExternal, identityContext);
            return;
        }
        ExecutableTask failedTask = failedExecutable.get();

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

        // Uma missão de agente só se conclui pelo dono do lease (completeExternalTask com workerId). Quem quiser
        // tirá-la do agente modela isso no processo — ex.: um catch interruptivo "humano assumiu".
        if (taskToComplete.type() == ExternalTaskType.AGENT) {
            throw new ExternalTaskLockException("ExternalTask " + externalTaskId
                    + " é uma missão de agente: conclua com o workerId do lease.");
        }

        return completeExternalTask(taskToComplete, variables, identityContext.actorId());
    }

    /**
     * Núcleo do complete, sem verificação de tenant — a tarefa já chega resolvida com o escopo de tenant
     * correto (seja pelo check acima, seja pela própria consulta por chave de correlação em
     * {@code findExternalTaskByCorrelationKey}, usada tanto por {@link #correlateMessage} quanto pelo
     * {@code EventThrowExecutor} de um nó EVENT_THROWER). {@code actorId} pode ser {@code null} quando não há
     * um ator humano/externo por trás do complete (ex.: correlação disparada internamente por um throw).
     */
    private static Map<String, ProcessVariable> changedVariables(Map<String, ProcessVariable> current,
                                                                 Map<String, ProcessVariable> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return Map.of();
        }
        Map<String, ProcessVariable> changed = new java.util.LinkedHashMap<>();
        submitted.forEach((name, variable) -> {
            ProcessVariable before = current != null ? current.get(name) : null;
            Object newValue = variable != null ? variable.value() : null;
            if (before == null || !sameValue(before.value(), newValue)) {
                changed.put(name, variable);
            }
        });
        return changed;
    }

    // 5000 (Integer, lido do Mongo) e 5000L/5000.0 (vindo do JSON) são o mesmo valor para quem lê o histórico.
    private static boolean sameValue(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return new java.math.BigDecimal(na.toString()).compareTo(new java.math.BigDecimal(nb.toString())) == 0;
        }
        return Objects.equals(a, b);
    }

    ProcessInstance completeExternalTask(ExternalTask taskToComplete, Map<String, ProcessVariable> variables, String actorId) {
        return completeExternalTask(taskToComplete, variables, actorId, List.of());
    }

    /**
     * @param leadingEvents eventos que entram no mesmo commit da conclusão, logo depois do
     *                      {@code EXTERNAL_TASK_COMPLETED} (ex.: {@code EVENT_CORRELATED}, {@code AGENT_MISSION_COMPLETED})
     */
    private ProcessInstance completeExternalTask(ExternalTask taskToComplete, Map<String, ProcessVariable> variables,
                                                 String actorId, List<OutboxEventEntity> leadingEvents) {
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(taskToComplete.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + taskToComplete.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);

        // Para o histórico, "o que este ator informou" é o que a conclusão acrescentou ou mudou — telas genéricas
        // (como o Monitor) reenviam a instância inteira, e gravar tudo esconderia a decisão no meio do payload.
        Map<String, ProcessVariable> changedVariables = changedVariables(processInstanceRecord.variables(), variables);

        if (variables != null) {
            processInstanceExecution.addVariables(variables);
        }

        FlowNodeDefinition completedNode = processDefinition.flowNodes().get(taskToComplete.taskDefinitionId());

        // inputs de uma EXTERNAL_TASK = dados exigidos para concluí-la (docs/engine/26). Validados contra o estado
        // já mesclado (um campo obrigatório pode ter vindo de uma etapa anterior), numa cópia — getVariables()
        // devolve um decorador que grava de volta na execução. Só EXTERNAL_TASK: EVENT_CATCHER e afins também
        // passam por aqui (correlateMessage/correlateFromThrow) e não declaram contrato de dados. Nada foi
        // persistido ainda, então a falha deixa a tarefa pendente como estava.
        // outputs (fase 2): o que precisa existir depois desta etapa — validado no mesmo estado mesclado e com a
        // mesma semântica síncrona (422 na API, tarefa continua pendente), já que quem conclui está esperando a
        // resposta. Numa EXECUTABLE_TASK a falha de output vira retry/incidente (ProcessExecutionManager).
        // AGENT_TASK tem o mesmo contrato: é o ponto em que o motor não confia no LLM (docs/engine/31).
        if ((completedNode instanceof ExternalTaskDefinition || completedNode instanceof AgentTaskDefinition)
                && completedNode instanceof VariableBindingAware bindingAware) {
            Map<String, ProcessVariable> mergedState = new HashMap<>(processInstanceExecution.getVariables());
            List<VariableValidationError> errors = new ArrayList<>(variableContractValidator.validate(
                    bindingAware.variableBindingsOrEmpty().inputs(), processDefinition, mergedState));
            errors.addAll(variableContractValidator.validate(
                    bindingAware.variableBindingsOrEmpty().outputs(), processDefinition, mergedState));
            if (!errors.isEmpty()) {
                throw new VariableValidationException(taskToComplete.taskDefinitionId(), errors);
            }
        }

        // completedNode.commitAfter(): quando true, força a continuação a ser tratada como assíncrona mesmo
        // que o próximo nó não declare commitBefore — a mesma semântica que ProcessExecutionManager já aplica
        // no loop síncrono (ver isCommitAfter ali). Sem isto, o campo era ignorado neste caminho e a
        // continuação de um EVENT_CATCHER/EXTERNAL_TASK sempre rodava inline, na mesma chamada de quem
        // completou a tarefa (webhook externo ou, agora, um EVENT_THROWER) — inclusive a execução de negócio
        // do segmento seguinte inteiro, não só a marcação de conclusão.
        boolean isCommitAfter = Boolean.TRUE.equals(completedNode.commitAfter());
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, processInstanceExecution.getVariables(), isCommitAfter);

        return continueFromExternalTask(taskToComplete, completedNode, continuation, processInstanceExecution,
                processDefinition, actorId, changedVariables, leadingEvents);
    }

    /**
     * Executa a continuação já decidida a partir de uma tarefa externa e persiste tudo num commit — a saída
     * normal ({@link #completeExternalTask}) ou a de um {@code BOUNDARY_ERROR_HANDLER} ({@link #throwBusinessError}).
     */
    private ProcessInstance continueFromExternalTask(ExternalTask taskToComplete, FlowNodeDefinition completedNode,
                                                     Continuation continuation,
                                                     ProcessInstanceExecution processInstanceExecution,
                                                     ProcessDefinition processDefinition, String actorId,
                                                     Map<String, ProcessVariable> changedVariables,
                                                     List<OutboxEventEntity> leadingEvents) {
        ExecutionResult executionResult;

        if (continuation == null || continuation.nextNodes().isEmpty()) {
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), null);

        } else if (continuation.isAsynchronous()) {
            // completedNode.commitAfter() fez determineNextContinuation marcar isso como assíncrono: não roda
            // o próximo nó inline nesta chamada — ContinuationService.handleContinuation (chamado no fim deste
            // método) vê Continuation.isAsynchronous()=true e, em vez de executar o handler do próximo nó,
            // apenas persiste a próxima ExecutableTask/ExternalTask a partir de continuation.nextNodes(). Quem
            // executa de fato é quem retomar depois (TaskAcquirer/executeFromTask), em transação própria.
            executionResult = new ExecutionResult(new ExecutionOutcome(processInstanceExecution, Collections.emptyList()), continuation);

        } else {

            FlowNodeDefinition startPoint = continuation.nextNodes().get(0);
            String startPointKey = continuation.nextNodeKeys().get(0);

            // Boundary catch event interruptivo: guarda o primeiro ExecutableTaskDefinition alcançado nesta
            // chamada (mesmo que não seja o próximo nó imediato — gateways/end event no meio continuam
            // avaliados normalmente). Sem isso, um handler com efeito colateral real na saída do catch event
            // rodaria de verdade antes do guard de finalização em commitWork decidir quem venceu a corrida —
            // ver docs/engine/19-guard-de-finalizacao-boundary-events.md, seção "fluxo fantasma".
            boolean guardSynchronousHandlers = completedNode instanceof InterruptiveCatchEventDefinition;

            try {
                executionResult = processExecutionManager.executeFlow(
                        startPoint,
                        startPointKey,
                        taskToComplete.branchId(),
                        taskToComplete.joinTaskId(),
                        processInstanceExecution,
                        processDefinition,
                        false,
                        guardSynchronousHandlers
                );
            } catch (Exception ex) {

                Throwable rootCause = (ex instanceof FlowNodeExecutionFailure && ex.getCause() != null) ? ex.getCause() : ex;

                if(kikwiflowConfig.isStatsEnabled()){
                    SyncContinuationFailed telemetryEvent = new SyncContinuationFailed(
                            processInstanceExecution.getId(),
                            taskToComplete.id(),
                            startPointKey,
                            rootCause.getMessage(),
                            FailureHandler.getStackTrace(rootCause),
                            Instant.now()
                    );

                    this.asynchronousEventPublisher.publishEvent(telemetryEvent);
                }

                throw new RuntimeException("Kikwiflow Core: Falha síncrona no nó [" + startPointKey + "] após a conclusão da tarefa externa [" + taskToComplete.id() + "]. Transação abortada.", rootCause);
            }
        }

        if (leadingEvents != null && !leadingEvents.isEmpty()) {
            List<OutboxEventEntity> events = new ArrayList<>(leadingEvents);
            events.addAll(executionResult.outcome().events());
            executionResult = new ExecutionResult(
                    new ExecutionOutcome(executionResult.outcome().processInstance(), events), executionResult.continuation());
        }

        return continuationService.handleContinuation(executionResult, taskToComplete, processDefinition, actorId, changedVariables);
    }

    /**
     * Correlaciona uma mensagem/evento externo (webhook, callback assíncrono) a um nó EVENT_CATCHER aguardando
     * essa chave de negócio. O chamador nunca precisa conhecer {@code processInstanceId}/{@code taskId} — só a
     * própria chave de correlação.
     *
     * <p>Em modo STANDALONE, a tarefa encontrada É o ponto de espera e é completada exatamente como
     * {@link #completeExternalTask}. Em modo GROUP, a tarefa encontrada é uma das N tarefas-filhas: a chave é
     * resolvida atomicamente contra a tarefa-mãe (ver {@code KikwiEngineRepository.resolveCorrelationChild});
     * se essa chamada foi a responsável por satisfazer a {@code matchPolicy}, a tarefa-mãe é completada da
     * mesma forma — caso contrário, apenas as variáveis recebidas são persistidas e o fluxo não avança ainda.
     *
     * @throws TaskNotFoundException se nenhum EVENT_CATCHER ativo estiver aguardando a chave informada —
     *         inclui o caso de uma correlação duplicada para uma chave já consumida (proteção de idempotência
     *         natural: a tarefa já foi apagada na primeira entrega).
     */
    public ProcessInstance correlateMessage(String correlationKey, Map<String, ProcessVariable> variables, IdentityContext identityContext) {
        ExternalTask hit = kikwiEngineRepository.findExternalTaskByCorrelationKey(correlationKey, identityContext.tenantId())
                .orElseThrow(() -> new TaskNotFoundException(
                        "Nenhum EVENT_CATCHER ativo aguardando a chave de correlação: " + correlationKey));

        return resolveAndComplete(correlationKey, hit, variables, identityContext.actorId());
    }

    /**
     * Contraparte interna de {@link #correlateMessage}, usada pelo {@code EventThrowExecutor} de um nó
     * EVENT_THROWER: a chave já é resolvida com o tenant da própria instância que está lançando o evento (não
     * há {@link IdentityContext} de um chamador externo aqui), e não há {@code actorId} humano associado —
     * política v1 é FAIL: se ninguém estiver esperando essa chave, a {@link TaskNotFoundException} propaga e
     * vira falha do próprio nó de throw (incident/retry normal quando o throw roda como ExecutableTask
     * assíncrona; aborta a transação síncrona corrente quando roda inline — mesmo comportamento de qualquer
     * outra falha de nó nesses dois caminhos).
     */
    public ProcessInstance correlateFromThrow(String correlationKey, String tenantId, Map<String, ProcessVariable> variables) {
        ExternalTask hit = kikwiEngineRepository.findExternalTaskByCorrelationKey(correlationKey, tenantId)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Kikwiflow Engine: nenhum EVENT_CATCHER ativo aguardando a chave de correlação lançada: " + correlationKey));

        return resolveAndComplete(correlationKey, hit, variables, null);
    }

    private ProcessInstance resolveAndComplete(String correlationKey, ExternalTask hit, Map<String, ProcessVariable> variables,
                                               String actorId) {
        List<OutboxEventEntity> correlatedEvents = new ArrayList<>();
        criticalEventRecorder.recordEventCorrelated(correlatedEvents, correlationKey, hit, actorId, variables);

        if (hit.coordinatorTaskId() == null) {
            // STANDALONE — a própria tarefa encontrada é o ponto de espera
            return completeExternalTask(hit, variables, actorId, correlatedEvents);
        }

        // GROUP: "hit" é uma tarefa-filha — resolve atomicamente contra a tarefa-mãe (coordinatorTaskId)
        boolean satisfied = kikwiEngineRepository.resolveCorrelationChild(hit.id(), hit.coordinatorTaskId(), hit.matchPolicy());

        if (!satisfied) {
            // matchPolicy ainda não satisfeita (ex.: ALL restando outras chaves): persiste as variáveis
            // recebidas, o fluxo principal não avança ainda. O EVENT_CORRELATED é o único registro desta entrega.
            return kikwiEngineRepository.addVariables(hit.processInstanceId(), variables, correlatedEvents);
        }

        // esta chamada foi a responsável por satisfazer a matchPolicy — completa a tarefa-mãe normalmente
        ExternalTask parent = kikwiEngineRepository.findExternalTaskById(hit.coordinatorTaskId())
                .orElseThrow(() -> new TaskNotFoundException("Kikwiflow Engine: tarefa coordenadora não encontrada: " + hit.coordinatorTaskId()));
        return completeExternalTask(parent, variables, actorId, correlatedEvents);
    }


    // ===================================================================================================
    // Protocolo de worker com lease (docs/engine/31, E2) e atividades de missão de agente (E3).
    // Vale para toda ExternalTask com topicName (hoje: as de AGENT_TASK). As demais seguem o fluxo humano
    // claim/complete, sem mudança.
    // ===================================================================================================

    /** Atividades que o runtime pode reportar. {@code AGENT_MISSION_COMPLETED} é do motor, no complete. */
    private static final Set<String> REPORTABLE_ACTIVITY_TYPES = Set.of(
            CriticalEventType.AGENT_MISSION_STARTED.name(),
            CriticalEventType.AGENT_TOOL_INVOKED.name(),
            CriticalEventType.AGENT_HANDOFF.name());
    private static final int MAX_ACTIVITY_DATA_CHARS = 8 * 1024;
    private static final int MAX_ACTIVITIES_PER_CALL = 100;
    private static final int MAX_ERROR_MESSAGE_CHARS = 2000;

    /**
     * Adquire até {@code maxTasks} tarefas dos {@code topics} com lease de {@code lockDuration}. Um lease vencido
     * devolve a tarefa à fila sem varredura nenhuma: a próxima chamada de qualquer worker a pega.
     *
     * @param identityContext o tenant da identidade restringe a aquisição; {@link IdentityContext#system()}
     *                        adquire de qualquer tenant (runtime de agentes embutido, que atende todos)
     */
    public List<LockedExternalTask> fetchAndLock(String workerId, Collection<String> topics, int maxTasks,
                                                 Duration lockDuration, IdentityContext identityContext) {
        requireWorkerId(workerId);
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("fetchAndLock exige ao menos um tópico.");
        }
        if (maxTasks < 1) {
            throw new IllegalArgumentException("maxTasks deve ser >= 1.");
        }
        requirePositive(lockDuration);

        Instant now = Instant.now();
        String tenantId = identityContext.isSystem() ? null : identityContext.tenantId();
        List<ExternalTask> locked = kikwiEngineRepository.fetchAndLockExternalTasks(workerId, topics, tenantId, maxTasks,
                now, now.plus(lockDuration), task -> {
                    List<OutboxEventEntity> events = new ArrayList<>();
                    criticalEventRecorder.recordExternalTaskLocked(events, task);
                    return events;
                });

        List<LockedExternalTask> result = new ArrayList<>();
        for (ExternalTask task : locked) {
            toLockedExternalTask(task).ifPresent(result::add);
        }
        return result;
    }

    /**
     * Trava uma tarefa escolhida pelo worker (ex.: de uma lista de missões disponíveis), com as mesmas regras do
     * {@link #fetchAndLock}. Com o lease já deste worker, só renova.
     *
     * @throws TaskNotFoundException      a tarefa não existe (concluída, ou interrompida por um boundary event)
     * @throws ExternalTaskLockException  outro worker tem o lease, ou a tarefa não está adquirível (retries
     *                                    esgotados, aguardando o retry, concluída) — REST: 409
     */
    public LockedExternalTask lockExternalTask(String externalTaskId, String workerId, Duration lockDuration,
                                               IdentityContext identityContext) {
        requireWorkerId(workerId);
        requirePositive(lockDuration);
        ExternalTask task = kikwiEngineRepository.findExternalTaskById(externalTaskId)
                .orElseThrow(() -> new TaskNotFoundException("ExternalTask not found with id: " + externalTaskId
                        + " (concluída, ou interrompida por um boundary event)"));
        if (!identityContext.isSystem() && !Objects.equals(task.tenantId(), identityContext.tenantId())) {
            throw new SecurityException("Tenant mismatch: Task " + externalTaskId + " does not belong to the provided tenant.");
        }
        Instant now = Instant.now();
        ExternalTask locked;
        if (task.isLockedBy(workerId, now)) {
            locked = extendLock(externalTaskId, workerId, lockDuration, identityContext);
        } else {
            locked = kikwiEngineRepository.lockExternalTask(externalTaskId, workerId,
                    identityContext.isSystem() ? null : identityContext.tenantId(), now, now.plus(lockDuration), t -> {
                        List<OutboxEventEntity> events = new ArrayList<>();
                        criticalEventRecorder.recordExternalTaskLocked(events, t);
                        return events;
                    }).orElseThrow(() -> new ExternalTaskLockException("A ExternalTask " + externalTaskId
                    + " não está disponível para o worker " + workerId
                    + " (outro worker tem o lease, os retries acabaram ou ela espera o próximo retry)."));
        }
        return toLockedExternalTask(locked).orElseThrow(() -> new TaskNotFoundException(
                "A instância ou a definição da ExternalTask " + externalTaskId + " não existe mais."));
    }

    /** Renova o lease. Falha (409) se o lease não é mais de {@code workerId}; 404 se a tarefa sumiu. */
    public ExternalTask extendLock(String externalTaskId, String workerId, Duration lockDuration,
                                   IdentityContext identityContext) {
        requirePositive(lockDuration);
        ExternalTask task = findLeasedTask(externalTaskId, workerId, identityContext);
        Instant now = Instant.now();
        ExternalTask renewed = task.toBuilder().lockExpiresAt(now.plus(lockDuration)).build();
        if (!kikwiEngineRepository.updateLockedExternalTask(renewed, workerId, now, null, null)) {
            throw lockLost(externalTaskId, workerId);
        }
        return renewed;
    }

    /**
     * Conclusão pelo dono do lease. Mesma validação de contrato do complete humano (422). Numa missão de agente
     * emite também {@code AGENT_MISSION_COMPLETED} no mesmo commit, com o digest das saídas.
     *
     * @param missionReport resumo opcional do runtime (ex.: {@code turns}, {@code usage}); mesmas regras de
     *                      conteúdo de {@link #reportActivities}
     */
    public ProcessInstance completeExternalTask(String externalTaskId, String workerId,
                                                Map<String, ProcessVariable> variables,
                                                Map<String, Object> missionReport,
                                                IdentityContext identityContext) {
        ExternalTask task = findLeasedTask(externalTaskId, workerId, identityContext);

        List<OutboxEventEntity> leadingEvents = new ArrayList<>();
        if (task.type() == ExternalTaskType.AGENT) {
            validateActivityData(missionReport);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            List<String> outputKeys = outputKeysOf(task);
            data.put("outputs", outputKeys);
            data.put("outputsDigest", outputsDigest(outputKeys, variables));
            if (missionReport != null && !missionReport.isEmpty()) {
                data.put("report", missionReport);
            }
            criticalEventRecorder.recordAgentActivity(leadingEvents, CriticalEventType.AGENT_MISSION_COMPLETED, task,
                    workerId, Instant.now(), data);
        }

        return completeExternalTask(task, variables, workerId, leadingEvents);
    }

    /**
     * Falha técnica do worker. Com {@code retries > 0} a tarefa volta a ser adquirível depois de
     * {@code retryTimeout}; com {@code 0} abre um incidente no próprio nó ({@code retryIncident} o resolve).
     */
    public void handleFailure(String externalTaskId, String workerId, String errorMessage, String errorDetails,
                              int retries, Duration retryTimeout, IdentityContext identityContext) {
        ExternalTask task = findLeasedTask(externalTaskId, workerId, identityContext);
        Instant now = Instant.now();
        int remaining = Math.max(0, retries);
        String message = truncate(errorMessage != null ? errorMessage : "Falha reportada pelo worker " + workerId);

        ExternalTask failed = task.toBuilder()
                .lockOwner(null)
                .lockExpiresAt(null)
                .retries(remaining)
                .availableAt(remaining > 0 && retryTimeout != null && !retryTimeout.isNegative() ? now.plus(retryTimeout) : null)
                .errorMessage(message)
                .build();

        List<OutboxEventEntity> events = new ArrayList<>();
        criticalEventRecorder.recordExternalTaskFailed(events, failed, workerId);

        List<Incident> incidents = null;
        if (remaining == 0) {
            Incident incident = newExternalTaskIncident(task, "FAILED_EXTERNAL_TASK", message, errorDetails, now);
            criticalEventRecorder.recordIncidentCreated(events, incident, task.tenantId());
            incidents = List.of(incident);
        }

        if (!kikwiEngineRepository.updateLockedExternalTask(failed, workerId, now, incidents, events)) {
            throw lockLost(externalTaskId, workerId);
        }
    }

    /**
     * Erro de negócio do worker (ex.: o lead pediu opt-out): segue pelo {@code BOUNDARY_ERROR_HANDLER} do nó com
     * esse {@code errorCode} (ou o curinga). As saídas do nó não são exigidas nesse caminho. Sem tratador, abre
     * um incidente {@code UNHANDLED_BUSINESS_ERROR} e a tarefa fica parada, como numa {@code EXECUTABLE_TASK}.
     */
    public ProcessInstance throwBusinessError(String externalTaskId, String workerId, String errorCode,
                                              String errorMessage, Map<String, ProcessVariable> variables,
                                              IdentityContext identityContext) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("throwBusinessError exige errorCode.");
        }
        ExternalTask task = findLeasedTask(externalTaskId, workerId, identityContext);

        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(task.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + task.processInstanceId()));
        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();
        FlowNodeDefinition node = processDefinition.flowNodes().get(task.taskDefinitionId());

        Optional<ErrorHandlerDefinition> handler = navigator.findMatchingErrorHandler(node, processDefinition, errorCode);
        if (handler.isEmpty()) {
            Instant now = Instant.now();
            String message = truncate("Erro de negócio '" + errorCode + "' sem BOUNDARY_ERROR_HANDLER"
                    + (errorMessage != null ? ": " + errorMessage : ""));
            ExternalTask stuck = task.toBuilder().lockOwner(null).lockExpiresAt(null).retries(0).errorMessage(message).build();
            List<OutboxEventEntity> events = new ArrayList<>();
            Incident incident = newExternalTaskIncident(task, "UNHANDLED_BUSINESS_ERROR", message, null, now);
            criticalEventRecorder.recordIncidentCreated(events, incident, task.tenantId());
            if (!kikwiEngineRepository.updateLockedExternalTask(stuck, workerId, now, List.of(incident), events)) {
                throw lockLost(externalTaskId, workerId);
            }
            return processInstanceRecord;
        }

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);
        Map<String, ProcessVariable> changedVariables = changedVariables(processInstanceRecord.variables(), variables);
        if (variables != null) {
            processInstanceExecution.addVariables(variables);
        }

        ErrorHandlerDefinition errorHandler = handler.get();
        List<FlowNodeDefinition> nextNodes = errorHandler.outgoing().stream()
                .map(flow -> processDefinition.flowNodes().get(flow.targetNodeId()))
                .toList();
        List<String> nextNodeKeys = errorHandler.outgoing().stream().map(flow -> flow.targetNodeId()).toList();
        Continuation continuation = new Continuation(nextNodes, nextNodeKeys, Boolean.TRUE.equals(errorHandler.commitAfter()));

        List<OutboxEventEntity> leadingEvents = new ArrayList<>();
        criticalEventRecorder.recordInterruptedFlowNode(leadingEvents, processInstanceExecution, processDefinition,
                task.taskDefinitionId(), errorHandler.id());

        return continueFromExternalTask(task, node, continuation, processInstanceExecution, processDefinition,
                workerId, changedVariables, leadingEvents);
    }

    /**
     * Anexa atividades de missão ao histórico (append-only, só o dono do lease). {@code data} leva só hashes, ids
     * e referências: no máximo {@value #MAX_ACTIVITY_DATA_CHARS} caracteres por atividade, valores
     * {@code String}/{@code Number}/{@code Boolean}/listas/mapas.
     */
    public void reportActivities(String externalTaskId, String workerId, List<AgentActivityReport> activities,
                                 IdentityContext identityContext) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        if (activities.size() > MAX_ACTIVITIES_PER_CALL) {
            throw new IllegalArgumentException("No máximo " + MAX_ACTIVITIES_PER_CALL + " atividades por chamada.");
        }
        ExternalTask task = findLeasedTask(externalTaskId, workerId, identityContext);
        if (task.type() != ExternalTaskType.AGENT) {
            throw new IllegalArgumentException("Atividades AGENT_* só valem para missões de AGENT_TASK.");
        }

        List<OutboxEventEntity> events = new ArrayList<>();
        for (AgentActivityReport activity : activities) {
            if (activity == null || !REPORTABLE_ACTIVITY_TYPES.contains(activity.type())) {
                throw new IllegalArgumentException("Tipo de atividade não aceito: "
                        + (activity != null ? activity.type() : null) + ". Aceitos: " + REPORTABLE_ACTIVITY_TYPES);
            }
            validateActivityData(activity.data());
            criticalEventRecorder.recordAgentActivity(events, CriticalEventType.valueOf(activity.type()), task, workerId,
                    activity.at(), activity.data());
        }

        if (!kikwiEngineRepository.updateLockedExternalTask(task, workerId, Instant.now(), null, events)) {
            throw lockLost(externalTaskId, workerId);
        }
    }

    private ExternalTask findLeasedTask(String externalTaskId, String workerId, IdentityContext identityContext) {
        requireWorkerId(workerId);
        ExternalTask task = kikwiEngineRepository.findExternalTaskById(externalTaskId)
                .orElseThrow(() -> new TaskNotFoundException("ExternalTask not found with id: " + externalTaskId
                        + " (concluída, ou interrompida por um boundary event)"));
        if (!identityContext.isSystem() && !Objects.equals(task.tenantId(), identityContext.tenantId())) {
            throw new SecurityException("Tenant mismatch: Task " + externalTaskId + " does not belong to the provided tenant.");
        }
        if (!task.isLockedBy(workerId, Instant.now())) {
            throw lockLost(externalTaskId, workerId);
        }
        return task;
    }

    private Optional<LockedExternalTask> toLockedExternalTask(ExternalTask task) {
        Optional<ProcessInstance> instance = kikwiEngineRepository.findProcessInstanceById(task.processInstanceId());
        Optional<ProcessDefinition> definition = processDefinitionService.getById(task.processDefinitionId());
        if (instance.isEmpty() || definition.isEmpty()) {
            return Optional.empty();
        }
        FlowNodeDefinition node = definition.get().flowNodes().get(task.taskDefinitionId());
        Map<String, ProcessVariable> all = instance.get().variables() != null ? instance.get().variables() : Map.of();

        Map<String, ProcessVariable> variables = all;
        List<String> declarationKeys = new ArrayList<>();
        if (node instanceof VariableBindingAware aware) {
            List<String> inputKeys = aware.variableBindingsOrEmpty().inputs().stream().map(b -> b.variable()).toList();
            if (!inputKeys.isEmpty()) {
                Map<String, ProcessVariable> inputs = new java.util.LinkedHashMap<>();
                inputKeys.forEach(key -> { if (all.containsKey(key)) inputs.put(key, all.get(key)); });
                variables = inputs;
            }
            declarationKeys.addAll(inputKeys);
            aware.variableBindingsOrEmpty().outputs().forEach(b -> declarationKeys.add(b.variable()));
        }
        List<VariableDeclaration> declarations = definition.get().variableDeclarations() == null ? List.of()
                : definition.get().variableDeclarations().stream().filter(d -> declarationKeys.contains(d.key())).toList();

        return Optional.of(new LockedExternalTask(task, instance.get().businessKey(), variables, node, declarations));
    }

    private List<String> outputKeysOf(ExternalTask task) {
        return processDefinitionService.getById(task.processDefinitionId())
                .map(definition -> definition.flowNodes().get(task.taskDefinitionId()))
                .filter(node -> node instanceof VariableBindingAware)
                .map(node -> ((VariableBindingAware) node).variableBindingsOrEmpty().outputs().stream()
                        .map(b -> b.variable()).toList())
                .orElse(List.of());
    }

    /** SHA-256 de {@code chave=valor} das saídas enviadas, em ordem de chave: prova o que foi entregue sem gravar o valor. */
    private static String outputsDigest(List<String> outputKeys, Map<String, ProcessVariable> variables) {
        StringBuilder canonical = new StringBuilder();
        new java.util.TreeSet<>(outputKeys).forEach(key -> {
            ProcessVariable variable = variables != null ? variables.get(key) : null;
            canonical.append(key).append('=').append(variable != null ? String.valueOf(variable.value()) : "").append('\n');
        });
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateActivityData(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        if (activityDataSize(data) > MAX_ACTIVITY_DATA_CHARS) {
            throw new IllegalArgumentException("data de atividade acima de " + MAX_ACTIVITY_DATA_CHARS
                    + " caracteres: grave só hashes, ids e referências (claim check).");
        }
    }

    private static int activityDataSize(Object value) {
        if (value == null || value instanceof Boolean) {
            return 5;
        }
        if (value instanceof Number number) {
            return number.toString().length();
        }
        if (value instanceof String text) {
            return text.length() + 2;
        }
        if (value instanceof Map<?, ?> map) {
            int size = 2;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                size += String.valueOf(entry.getKey()).length() + 3 + activityDataSize(entry.getValue());
            }
            return size;
        }
        if (value instanceof Collection<?> list) {
            int size = 2;
            for (Object item : list) {
                size += activityDataSize(item) + 1;
            }
            return size;
        }
        throw new IllegalArgumentException("Valor não aceito em data de atividade: " + value.getClass().getSimpleName()
                + ". Use String, Number, Boolean, listas e mapas.");
    }

    private static Incident newExternalTaskIncident(ExternalTask task, String type, String message, String details,
                                                    Instant now) {
        return new Incident(UUID.randomUUID().toString(), type, message, details, task.processDefinitionId(),
                task.processInstanceId(), task.id(), now, IncidentStatus.OPEN, task.taskDefinitionId());
    }

    private void retryExternalTaskIncident(Incident incident, ExternalTask failedTask, IdentityContext identityContext) {
        ExternalTask restored = failedTask.toBuilder()
                .retries(null)
                .availableAt(null)
                .errorMessage(null)
                .lockOwner(null)
                .lockExpiresAt(null)
                .build();
        Incident resolvedIncident = new Incident(incident.id(), incident.type(), incident.message(), incident.stackTrace(),
                incident.processDefinitionId(), incident.processInstanceId(), incident.executionId(),
                incident.createdAt(), IncidentStatus.RESOLVED, incident.taskDefinitionId());

        List<OutboxEventEntity> events = new ArrayList<>();
        criticalEventRecorder.recordIncidentResolved(events, resolvedIncident, failedTask.tenantId(), identityContext.actorId());
        kikwiEngineRepository.saveExternalTask(restored, List.of(resolvedIncident), events);
    }

    private static ExternalTaskLockException lockLost(String externalTaskId, String workerId) {
        return new ExternalTaskLockException("O worker " + workerId + " não tem o lease da ExternalTask "
                + externalTaskId + " (outro worker a adquiriu ou o lease venceu).");
    }

    private static void requireWorkerId(String workerId) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId é obrigatório.");
        }
    }

    private static void requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("lockDuration deve ser positiva.");
        }
    }

    private static String truncate(String message) {
        return message != null && message.length() > MAX_ERROR_MESSAGE_CHARS
                ? message.substring(0, MAX_ERROR_MESSAGE_CHARS) : message;
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

        // Timer de borda interruptivo: guarda o primeiro ExecutableTaskDefinition alcançado nesta chamada —
        // mesma razão do caminho de catch event em completeExternalTask (ver
        // docs/engine/19-guard-de-finalizacao-boundary-events.md, seção "fluxo fantasma"). Qualquer outro tipo
        // de ExecutableTask (inclusive um timer não-interruptivo) não guarda nada — não há corrida de
        // finalização a proteger.
        boolean guardSynchronousHandlers = executableTask.type() == ExecutableTaskType.INTERRUPTIVE_TIMER;

        try {
            // CALL_ACTIVITY_STARTER nunca passa pelo caminho genérico FlowNodeExecutor/TaskExecutor/
            // Navigator — é um dispatch dedicado (ver docs/engine/20-subprocessos-call-activity-especificacao.md,
            // §4.3): inicia a instância filha e se auto-apaga, sem gerar continuação nenhuma no fluxo do pai.
            // Fica aqui (não em ProcessExecutionManager) porque precisa de ProcessDefinitionService/
            // ContinuationService/ProcessStarter — o mesmo nível de orquestração cross-instance já usado por
            // completeExternalTask/correlateMessage/retryIncident nesta classe.
            if (executableTask.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER) {
                return executeCallActivityStarter(executableTask, processInstanceRecord, (CallActivityDefinition) flowNodeDefinition);
            }

            // Coordenadora em modo SEQUENTIAL com elementos ainda não iniciados: dispatch dedicado, mesma
            // razão do CALL_ACTIVITY_STARTER acima — não pode cair no caminho genérico Navigator/
            // handleContinuation, que resolveria as arestas de saída da coordenadora prematuramente, antes de
            // todos os elementos da coleção serem processados. Ver
            // docs/engine/20-subprocessos-call-activity-especificacao.md.
            if (executableTask.type() == ExecutableTaskType.CALL_ACTIVITY_COORDINATOR
                    && executableTask.pendingLoopElements() != null && !executableTask.pendingLoopElements().isEmpty()) {
                return advanceSequentialCallActivity(executableTask, processInstanceRecord);
            }

            executionResult = processExecutionManager.executeFlow(
                    flowNodeDefinition,
                    executableTask.taskDefinitionId(),
                    executableTask.branchId(),
                    executableTask.joinTaskId(),
                    processInstanceExecution,
                    processDefinition,
                    true,
                    guardSynchronousHandlers
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

    /**
     * Dispatch dedicado de uma iniciadora ({@code CALL_ACTIVITY_STARTER}) — ver
     * docs/engine/20-subprocessos-call-activity-especificacao.md, §4.3. Inicia a instância filha via
     * {@code startProcess()} (contrato pelo-menos-uma-vez: se o processo crashar entre o filho ser criado e
     * este método commitar a própria conclusão, o retry chama {@code startProcess()} de novo — mesmo contrato
     * que qualquer outro efeito colateral disparado por uma {@code ExecutableTask} já tem hoje) e apaga a
     * iniciadora, sem gerar continuação nenhuma no fluxo do pai: a coordenadora continua em
     * {@code AWAITING_BRANCHES} até o retorno do filho (ver {@code ContinuationService.handleContinuation},
     * ramo de registro de {@code BranchPullIntention} cross-instância). Retorna a {@code ProcessInstance} do
     * filho recém-criado — não a do pai, que fica estruturalmente inalterada por esta chamada além da própria
     * exclusão da iniciadora — porque é o dado relevante para quem chamou {@code executeFromTask} aqui.
     * Qualquer exceção (ex.: {@code calledElement} não encontrado) propaga para o catch de
     * {@code executeFromTask}, que já isola retry/incident a esta tarefa/branch especificamente.
     */
    private ProcessInstance executeCallActivityStarter(ExecutableTask starterTask, ProcessInstance parentInstance,
                                                        CallActivityDefinition callActivity) {

        Map<String, ProcessVariable> childVariables = new HashMap<>(
                parentInstance.variables() != null ? parentInstance.variables() : Map.of());

        if (callActivity.elementVariable() != null && starterTask.loopElement() != null) {
            childVariables.put(callActivity.elementVariable(), starterTask.loopElement());
        }

        String childBusinessKey = callActivity.collectionVariable() != null
                ? parentInstance.businessKey() + "#" + starterTask.loopIndex()
                : parentInstance.businessKey();

        ProcessInstance childInstance = this.startProcess()
                .byKey(callActivity.calledElement())
                .withBusinessKey(childBusinessKey)
                .onTenant(parentInstance.tenantId())
                .withVariables(childVariables)
                .from("CALL_ACTIVITY")
                .asChildOf(parentInstance.id(), starterTask.joinTaskId(), starterTask.branchId())
                .execute();

        UnitOfWork uow = new UnitOfWork(
                null, null, null,
                null, null,
                List.of(starterTask.id()),
                null, null,
                null, null, null, null,
                null, null, null,
                null, null
        );

        kikwiEngineRepository.commitWork(uow);

        return childInstance;
    }

    /**
     * Dispatch dedicado de avanço de uma coordenadora {@code CALL_ACTIVITY_COORDINATOR} em modo
     * {@code SEQUENTIAL} — ver docs/engine/20-subprocessos-call-activity-especificacao.md. É chamado quando a
     * coordenadora é readquirida (após o filho do branch anterior completar, {@code pendingBranchIds} esvaziar
     * via o mesmo {@code BranchPullIntention} genérico de {@code PARALLEL_GATEWAY}/{@code JOIN_GATEWAY}, e
     * {@code status} flipar para {@code PENDING}) enquanto ainda há elementos não iniciados em
     * {@code pendingLoopElements}.
     *
     * <p>Cria a próxima iniciadora (índice = {@code loopIndex + 1}) e devolve a coordenadora para
     * {@code AWAITING_BRANCHES} com um único branch pendente (o recém-criado) — nunca gera continuação no
     * fluxo do pai. Quando {@code pendingLoopElements} finalmente esvaziar (última iteração), este método
     * deixa de ser chamado (guard em {@code executeFromTask}) e a coordenadora cai no caminho genérico normal,
     * resolvendo as arestas de saída como qualquer outro nó — nenhum código novo é necessário para esse caso
     * terminal.
     *
     * <p>Mesmo contrato pelo-menos-uma-vez de {@link #executeCallActivityStarter}: se o processo crashar entre
     * este commit e a confirmação da aquisição da tarefa, o retry chama este método de novo — a próxima
     * iniciadora recriada usa o mesmo {@code branchId} determinístico ({@code coordinatorId + ":" + nextIndex}),
     * então uma segunda tentativa não diverge do estado esperado.
     */
    private ProcessInstance advanceSequentialCallActivity(ExecutableTask coordinatorTask, ProcessInstance coordinatorInstance) {
        int nextIndex = coordinatorTask.loopIndex() + 1;
        String nextBranchId = coordinatorTask.id() + ":" + nextIndex;
        ProcessVariable nextLoopElement = coordinatorTask.pendingLoopElements().get(0);
        List<ProcessVariable> remainingLoopElements = new ArrayList<>(coordinatorTask.pendingLoopElements()
                .subList(1, coordinatorTask.pendingLoopElements().size()));

        long initialRetries = kikwiflowConfig.getDefaultMaxRetries();
        ExecutableTask nextStarter = ExecutableTask.builder()
                .id(UUID.randomUUID().toString())
                .processDefinitionId(coordinatorTask.processDefinitionId())
                .taskDefinitionId(coordinatorTask.taskDefinitionId())
                .processInstanceId(coordinatorTask.processInstanceId())
                .type(ExecutableTaskType.CALL_ACTIVITY_STARTER)
                .status(ExecutableTaskStatus.PENDING)
                .joinTaskId(coordinatorTask.id())
                .branchId(nextBranchId)
                .loopIndex(nextIndex)
                .loopElement(nextLoopElement)
                .retries(initialRetries)
                .build();

        ExecutableTask updatedCoordinator = coordinatorTask.toBuilder()
                .status(ExecutableTaskStatus.AWAITING_BRANCHES)
                .pendingBranchIds(List.of(nextBranchId))
                .pendingLoopElements(remainingLoopElements)
                .loopIndex(nextIndex)
                .build();

        UnitOfWork uow = new UnitOfWork(
                null, null, null,                    // instanceToCreate, instanceToUpdate, instanceToDelete
                List.of(nextStarter), null,          // executableTasksToCreate, externalTasksToCreate
                null,                                 // executableTasksToDelete
                List.of(updatedCoordinator),          // executableTasksToUpdate
                null,                                 // externalTasksToDelete
                null, null, null, null,               // events, incidentsToCreate, incidentsToUpdate, incidentsToResolve
                null, null,                           // finishedNodeDefinitions, branchPullIntentions
                null,                                 // variableOperations
                null, null                            // finalizingNodeId, finalizingNodeType
        );

        kikwiEngineRepository.commitWork(uow);

        return coordinatorInstance;
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
        private String parentInstanceId;
        private String callerTaskId;
        private String callerBranchId;


        private ProcessStarter(KikwiflowEngine engine) {
            this.engine = engine;
        }

        /**
         * Marca esta instância como filha de um {@code CALL_ACTIVITY_COORDINATOR} — usado exclusivamente por
         * {@link #executeCallActivityStarter}. {@code parentInstanceId}/{@code callerTaskId}/
         * {@code callerBranchId} são gravados diretamente na {@code ProcessInstance} recém-criada (ver
         * {@code ProcessInstanceFactory.create}) e propagam para os eventos {@code PROCESS_INSTANCE_STARTED}/
         * {@code PROCESS_INSTANCE_FINISHED} sem nenhum código adicional — esses campos já são lidos de
         * {@code ProcessInstanceExecution} pelo {@code CriticalEventRecorder} hoje.
         */
        public ProcessStarter asChildOf(String parentInstanceId, String callerTaskId, String callerBranchId) {
            this.parentInstanceId = parentInstanceId;
            this.callerTaskId = callerTaskId;
            this.callerBranchId = callerBranchId;
            return this;
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

            ProcessInstance processInstance = ProcessInstanceFactory.create(businessKey, processDefinition.id(), variables,
                    businessValue, tenantId, origin, parentInstanceId, callerTaskId, callerBranchId);
            ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstance);
            processInstanceExecution.setPersisted(false);

            String defaultStartPointId  = processDefinition.defaultStartPoint();
            FlowNodeDefinition defaultStartPoint = processDefinition.flowNodes().get(defaultStartPointId);
            Objects.requireNonNull(defaultStartPoint, "Malformed process definition: unknown default start point. The default start point needs to be declared in flow nodes map");

            // inputs do start = variáveis exigidas para iniciar (docs/engine/26). Falha antes de qualquer
            // persistência. Vale também para filhos de CALL_ACTIVITY, que chegam aqui pelo mesmo ProcessStarter
            // com as variáveis repassadas pelo pai.
            if (defaultStartPoint instanceof VariableBindingAware aware) {
                engine.variableContractValidator.validateOrThrow(defaultStartPointId,
                        aware.variableBindingsOrEmpty().inputs(), processDefinition,
                        variables != null ? variables : Map.of());
            }

            ExecutionResult executionResult;
            try {
                executionResult = engine.processExecutionManager.executeFlow(
                        defaultStartPoint,
                        defaultStartPointId,
                        null,
                        null,
                        processInstanceExecution,
                        processDefinition,
                        false,
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
