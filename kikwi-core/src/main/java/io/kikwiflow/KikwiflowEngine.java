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
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.lightweight.SyncContinuationFailed;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
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

        return completeExternalTask(taskToComplete, variables, identityContext.actorId());
    }

    /**
     * Núcleo do complete, sem verificação de tenant — a tarefa já chega resolvida com o escopo de tenant
     * correto (seja pelo check acima, seja pela própria consulta por chave de correlação em
     * {@code findExternalTaskByCorrelationKey}, usada tanto por {@link #correlateMessage} quanto pelo
     * {@code EventThrowExecutor} de um nó EVENT_THROWER). {@code actorId} pode ser {@code null} quando não há
     * um ator humano/externo por trás do complete (ex.: correlação disparada internamente por um throw).
     */
    ProcessInstance completeExternalTask(ExternalTask taskToComplete, Map<String, ProcessVariable> variables, String actorId) {
        ProcessInstance processInstanceRecord = kikwiEngineRepository.findProcessInstanceById(taskToComplete.processInstanceId())
                .orElseThrow(() -> new ProcessInstanceNotFoundException("Process Instance Not Found with id: " + taskToComplete.processInstanceId()));

        ProcessDefinition processDefinition = processDefinitionService.getById(processInstanceRecord.processDefinitionId())
                .orElseThrow();

        ProcessInstanceExecution processInstanceExecution = ProcessInstanceMapper.mapToInstanceExecution(processInstanceRecord);

        if (variables != null) {
            processInstanceExecution.addVariables(variables);
        }

        FlowNodeDefinition completedNode = processDefinition.flowNodes().get(taskToComplete.taskDefinitionId());

        // completedNode.commitAfter(): quando true, força a continuação a ser tratada como assíncrona mesmo
        // que o próximo nó não declare commitBefore — a mesma semântica que ProcessExecutionManager já aplica
        // no loop síncrono (ver isCommitAfter ali). Sem isto, o campo era ignorado neste caminho e a
        // continuação de um EVENT_CATCHER/EXTERNAL_TASK sempre rodava inline, na mesma chamada de quem
        // completou a tarefa (webhook externo ou, agora, um EVENT_THROWER) — inclusive a execução de negócio
        // do segmento seguinte inteiro, não só a marcação de conclusão.
        boolean isCommitAfter = Boolean.TRUE.equals(completedNode.commitAfter());
        Continuation continuation = navigator.determineNextContinuation(completedNode, processDefinition, processInstanceExecution.getVariables(), isCommitAfter);

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

            // Boundary catch event interruptivo: guarda o primeiro ExecutableTaskDefinition alcançado nesta
            // chamada (mesmo que não seja o próximo nó imediato — gateways/end event no meio continuam
            // avaliados normalmente). Sem isso, um handler com efeito colateral real na saída do catch event
            // rodaria de verdade antes do guard de finalização em commitWork decidir quem venceu a corrida —
            // ver docs/engine/19-guard-de-finalizacao-boundary-events.md, seção "fluxo fantasma".
            boolean guardSynchronousHandlers = completedNode instanceof InterruptiveCatchEventDefinition;

            try {
                executionResult = processExecutionManager.executeFlow(
                        startPoint,
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
                            startPoint.id(),
                            rootCause.getMessage(),
                            FailureHandler.getStackTrace(rootCause),
                            Instant.now()
                    );

                    this.asynchronousEventPublisher.publishEvent(telemetryEvent);
                }

                throw new RuntimeException("Kikwiflow Core: Falha síncrona no nó [" + startPoint.id() + "] após a conclusão da tarefa externa [" + taskToComplete.id() + "]. Transação abortada.", rootCause);
            }
        }

        return continuationService.handleContinuation(executionResult, taskToComplete, processDefinition, actorId);
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

        return resolveAndComplete(hit, variables, identityContext.actorId());
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

        return resolveAndComplete(hit, variables, null);
    }

    private ProcessInstance resolveAndComplete(ExternalTask hit, Map<String, ProcessVariable> variables, String actorId) {
        if (hit.coordinatorTaskId() == null) {
            // STANDALONE — a própria tarefa encontrada é o ponto de espera
            return completeExternalTask(hit, variables, actorId);
        }

        // GROUP: "hit" é uma tarefa-filha — resolve atomicamente contra a tarefa-mãe (coordinatorTaskId)
        boolean satisfied = kikwiEngineRepository.resolveCorrelationChild(hit.id(), hit.coordinatorTaskId(), hit.matchPolicy());

        if (!satisfied) {
            // matchPolicy ainda não satisfeita (ex.: ALL restando outras chaves): persiste as variáveis
            // recebidas, o fluxo principal não avança ainda.
            return kikwiEngineRepository.addVariables(hit.processInstanceId(), variables, List.of());
        }

        // esta chamada foi a responsável por satisfazer a matchPolicy — completa a tarefa-mãe normalmente
        ExternalTask parent = kikwiEngineRepository.findExternalTaskById(hit.coordinatorTaskId())
                .orElseThrow(() -> new TaskNotFoundException("Kikwiflow Engine: tarefa coordenadora não encontrada: " + hit.coordinatorTaskId()));
        return completeExternalTask(parent, variables, actorId);
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

            ExecutionResult executionResult;
            try {
                executionResult = engine.processExecutionManager.executeFlow(
                        defaultStartPoint,
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
