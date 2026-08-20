/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kikwiflow.execution.event;

import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.FailureHandler;
import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.ExternalTaskClaimed;
import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.event.ExternalTaskUnclaimed;
import io.kikwiflow.model.event.FlowNodeFinished;
import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.event.IncidentCreated;
import io.kikwiflow.model.event.IncidentResolved;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.event.ProcessInstanceStarted;
import io.kikwiflow.model.event.ProcessVariableChanged;
import io.kikwiflow.model.event.RetryScheduled;
import io.kikwiflow.model.event.TimerFired;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ponto único de construção dos outbox events críticos (ver {@link CriticalEventType}).
 * <p>
 * Substitui a construção procedural que antes ficava espalhada em {@code ProcessExecutionManager} e
 * {@code ContinuationService}, cada um com sua própria checagem de {@code kikwiflowConfig.isOutboxEventsEnabled()}
 * — aqui o flag é resolvido uma única vez, no construtor, e cada método {@code record*} decide sozinho se deve
 * construir e empilhar o evento. Os chamadores só passam os dados e a lista acumuladora; nunca precisam saber se
 * o outbox está ligado.
 */
public class CriticalEventRecorder {

    private final KikwiflowConfig kikwiflowConfig;

    public CriticalEventRecorder(KikwiflowConfig kikwiflowConfig) {
        this.kikwiflowConfig = kikwiflowConfig;
    }

    /**
     * Expõe o flag para que chamadores possam evitar montar dados caros (snapshots, mapeamentos) quando o
     * outbox está desligado — os métodos {@code record*} já o checam internamente antes de agir, então esse
     * método só existe para permitir pular trabalho *anterior* à chamada, não para replicar a decisão.
     */
    public boolean isEnabled() {
        return kikwiflowConfig.isOutboxEventsEnabled();
    }

    /**
     * Registra o resultado da execução de um nó de fluxo. Quando {@code error} não é {@code null}, os campos
     * de erro do evento são preenchidos a partir dele — independentemente de o nó ter ou não um boundary error
     * handler que trate a exceção (essa decisão já foi tomada antes de chegar aqui, refletida em
     * {@code snapshot.nodeExecutionStatus()}).
     */
    public void recordFlowNodeFinished(List<OutboxEventEntity> events, FlowNodeExecutionSnapshot snapshot, RuntimeException error) {
        if (!isEnabled()) return;

        FlowNodeFinished.Builder builder = FlowNodeFinished.builder()
                .flowNodeDefinitionId(snapshot.flowNodeDefinition().id())
                .flowNodeType(snapshot.flowNodeDefinition().type())
                .flowNodeName(snapshot.flowNodeDefinition().name())
                .flowNodeDescription(snapshot.flowNodeDefinition().description())
                .processInstanceId(snapshot.processInstance().id())
                .tenantId(snapshot.processInstance().tenantId())
                .processDefinitionId(snapshot.processDefinition().id())
                .processDefinitionKey(snapshot.processDefinition().key())
                .nodeExecutionStatus(snapshot.nodeExecutionStatus())
                .startedAt(snapshot.startedAt())
                .finishedAt(snapshot.finishedAt());

        if (error != null) {
            builder.errorType(error.getClass().getName())
                    .errorMessage(error.getMessage())
                    .errorStackTrace(FailureHandler.getStackTrace(error));
        }

        events.add(new OutboxEventEntity(CriticalEventType.FLOW_NODE_FINISHED, builder.build()));
    }

    /**
     * Registra a resolução de um gateway exclusivo — só faz sentido chamar quando o nó atual é um
     * {@link ExclusiveGatewayDefinition} e a navegação de fato produziu uma continuação.
     */
    public void recordGatewayAnswerResolved(List<OutboxEventEntity> events,
                                            ProcessInstanceExecution processInstance,
                                            ProcessDefinition processDefinition,
                                            ExclusiveGatewayDefinition gateway,
                                            Continuation continuation) {
        if (!isEnabled()) return;

        GatewayAnswerResolved answerEvent = new GatewayAnswerResolved(
                processInstance.getId(),
                processDefinition.id(),
                processInstance.getTenantId(),
                processDefinition.key(),
                gateway.id(),
                gateway.providerType(),
                gateway.providerBean(),
                gateway.providerVariable(),
                continuation.resolvedAnswer(),
                continuation.chosenFlowId(),
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.GATEWAY_ANSWER_RESOLVED, answerEvent));
    }

    /**
     * Registra a conclusão de uma instância de processo. Só produz o evento quando a instância de fato chegou
     * a {@link ProcessInstanceStatus#COMPLETED} — cancelamento e demais status terminais ainda não têm um
     * evento dedicado (ver catálogo de eventos propostos).
     */
    public void recordProcessInstanceFinished(List<OutboxEventEntity> events,
                                              ProcessInstanceExecution processInstanceExecution,
                                              ProcessDefinition processDefinition) {
        if (!isEnabled() || !ProcessInstanceStatus.COMPLETED.equals(processInstanceExecution.getStatus())) return;

        ProcessInstanceFinished processInstanceFinished = ProcessInstanceFinished.builder()
                .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                .processDefinitionKey(processDefinition != null ? processDefinition.key() : null)
                .processDefinitionVersion(processDefinition != null ? processDefinition.version() : null)
                .businessKey(processInstanceExecution.getBusinessKey())
                .id(processInstanceExecution.getId())
                .status(processInstanceExecution.getStatus())
                .variables(processInstanceExecution.getVariables())
                .startedAt(processInstanceExecution.getStartedAt())
                .endedAt(processInstanceExecution.getEndedAt())
                .businessValue(processInstanceExecution.getBusinessValue())
                .tenantId(processInstanceExecution.getTenantId())
                .origin(processInstanceExecution.getOrigin())
                .parentInstanceId(processInstanceExecution.getParentInstanceId())
                .callerTaskId(processInstanceExecution.getCallerTaskId())
                .callerBranchId(processInstanceExecution.getCallerBranchId())
                .build();

        events.add(new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_FINISHED, processInstanceFinished));
    }

    /**
     * Registra a interrupção de uma atividade principal por um boundary event (timer interruptivo, hoje o
     * único caso). Unifica a construção que antes se repetia quase idêntica para o caso "boundary anexado a
     * ExecutableTask" e "boundary anexado a ExternalTask" em {@code ContinuationService}.
     */
    public void recordInterruptedFlowNode(List<OutboxEventEntity> events,
                                          ProcessInstanceExecution processInstanceExecution,
                                          ProcessDefinition processDefinition,
                                          String interruptedNodeDefinitionId,
                                          String interruptedByNodeDefinitionId) {
        if (!isEnabled()) return;

        FlowNodeDefinition interruptedNodeDefinition = processDefinition.flowNodes().get(interruptedNodeDefinitionId);

        FlowNodeFinished interruptedEvent = FlowNodeFinished.builder()
                .flowNodeDefinitionId(interruptedNodeDefinitionId)
                .flowNodeType(interruptedNodeDefinition != null ? interruptedNodeDefinition.type() : null)
                .flowNodeName(interruptedNodeDefinition != null ? interruptedNodeDefinition.name() : null)
                .flowNodeDescription(interruptedNodeDefinition != null ? interruptedNodeDefinition.description() : null)
                .processInstanceId(processInstanceExecution.getId())
                .tenantId(processInstanceExecution.getTenantId())
                .processDefinitionId(processInstanceExecution.getProcessDefinitionId())
                .processDefinitionKey(processDefinition.key())
                .interruptedByNodeDefinitionId(interruptedByNodeDefinitionId)
                .finishedAt(Instant.now())
                .nodeExecutionStatus(NodeExecutionStatus.INTERRUPTED)
                .build();

        events.add(new OutboxEventEntity(CriticalEventType.FLOW_NODE_FINISHED, interruptedEvent));
    }

    /**
     * Registra o início de uma instância de processo — contraparte de {@link #recordProcessInstanceFinished}.
     * Chamado uma única vez, no primeiro {@code commitWork} da instância (ver {@code ContinuationService}).
     */
    public void recordProcessInstanceStarted(List<OutboxEventEntity> events,
                                             ProcessInstanceExecution processInstanceExecution,
                                             ProcessDefinition processDefinition,
                                             String actorId) {
        if (!isEnabled()) return;

        ProcessInstanceStarted startedEvent = new ProcessInstanceStarted(
                processInstanceExecution.getId(),
                processInstanceExecution.getBusinessKey(),
                processInstanceExecution.getProcessDefinitionId(),
                processDefinition != null ? processDefinition.key() : null,
                processDefinition != null ? processDefinition.version() : null,
                processInstanceExecution.getVariables(),
                processInstanceExecution.getStartedAt(),
                processInstanceExecution.getBusinessValue(),
                processInstanceExecution.getTenantId(),
                processInstanceExecution.getOrigin(),
                processInstanceExecution.getParentInstanceId(),
                processInstanceExecution.getCallerTaskId(),
                processInstanceExecution.getCallerBranchId(),
                actorId
        );

        events.add(new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_STARTED, startedEvent));
    }

    /**
     * Registra a abertura de um incidente (retries esgotados ou erro de negócio não tratado).
     * {@code actorId} é sempre {@link IdentityContext#system()} — quem chama este método é sempre
     * {@code FailureHandler} reagindo a uma falha de execução, nunca uma ação de usuário.
     */
    public void recordIncidentCreated(List<OutboxEventEntity> events, Incident incident, String tenantId) {
        if (!isEnabled()) return;

        IncidentCreated incidentEvent = new IncidentCreated(
                incident.id(),
                incident.type(),
                incident.message(),
                incident.processDefinitionId(),
                incident.processInstanceId(),
                tenantId,
                incident.executionId(),
                incident.taskDefinitionId(),
                IdentityContext.system().actorId(),
                incident.createdAt()
        );

        events.add(new OutboxEventEntity(CriticalEventType.INCIDENT_CREATED, incidentEvent));
    }

    /**
     * Registra a resolução manual de um incidente aberto (ver {@code KikwiflowEngine.retryIncident}).
     */
    public void recordIncidentResolved(List<OutboxEventEntity> events, Incident resolvedIncident, String tenantId, String actorId) {
        if (!isEnabled()) return;

        IncidentResolved incidentEvent = new IncidentResolved(
                resolvedIncident.id(),
                resolvedIncident.type(),
                resolvedIncident.processDefinitionId(),
                resolvedIncident.processInstanceId(),
                tenantId,
                resolvedIncident.executionId(),
                resolvedIncident.taskDefinitionId(),
                actorId,
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.INCIDENT_RESOLVED, incidentEvent));
    }

    /**
     * Registra o claim de uma tarefa externa (human/external task) por um assignee.
     */
    public void recordExternalTaskClaimed(List<OutboxEventEntity> events, ExternalTask task, String assignee, String actorId) {
        if (!isEnabled()) return;

        ExternalTaskClaimed claimedEvent = new ExternalTaskClaimed(
                task.id(),
                task.processDefinitionId(),
                task.processInstanceId(),
                task.tenantId(),
                task.taskDefinitionId(),
                assignee,
                actorId,
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.EXTERNAL_TASK_CLAIMED, claimedEvent));
    }

    /**
     * Registra o unclaim de uma tarefa externa. {@code task} é o estado *antes* do unclaim, para que o
     * evento carregue quem era o assignee sendo removido.
     */
    public void recordExternalTaskUnclaimed(List<OutboxEventEntity> events, ExternalTask task, String actorId) {
        if (!isEnabled()) return;

        ExternalTaskUnclaimed unclaimedEvent = new ExternalTaskUnclaimed(
                task.id(),
                task.processDefinitionId(),
                task.processInstanceId(),
                task.tenantId(),
                task.taskDefinitionId(),
                task.assignee(),
                actorId,
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.EXTERNAL_TASK_UNCLAIMED, unclaimedEvent));
    }

    /**
     * Registra o complete de uma tarefa externa. {@code task} é o estado *antes* do complete, para que o
     * evento carregue {@code assignee} tal como estava no momento — o motor não valida esse campo contra
     * {@code actorId}; {@code KikwiflowEngine.completeExternalTask} é soberano e sempre completa a tarefa
     * independentemente de quem a comandou. Esse par existe só para auditoria/observabilidade.
     */
    public void recordExternalTaskCompleted(List<OutboxEventEntity> events, ExternalTask task, String actorId) {
        if (!isEnabled()) return;

        ExternalTaskCompleted completedEvent = new ExternalTaskCompleted(
                task.id(),
                task.processDefinitionId(),
                task.processInstanceId(),
                task.tenantId(),
                task.taskDefinitionId(),
                task.assignee(),
                actorId,
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.EXTERNAL_TASK_COMPLETED, completedEvent));
    }

    /**
     * Registra um retry agendado (a tarefa falhou mas ainda não esgotou as tentativas — ver o ramo
     * {@code !evaluation.shouldCreateIncident()} de {@code FailureHandler.handleFailure}).
     * {@code actorId} é sempre {@link IdentityContext#system()} — retries são sempre uma decisão automática
     * do motor, nunca uma ação de usuário.
     */
    public void recordRetryScheduled(List<OutboxEventEntity> events, ExecutableTask task, long executionsSoFar,
                                     long retriesLeft, Instant nextDueDate, String errorMessage, String tenantId) {
        if (!isEnabled()) return;

        RetryScheduled retryEvent = new RetryScheduled(
                task.id(),
                task.processDefinitionId(),
                task.processInstanceId(),
                tenantId,
                task.taskDefinitionId(),
                executionsSoFar,
                retriesLeft,
                nextDueDate,
                errorMessage,
                IdentityContext.system().actorId(),
                Instant.now()
        );

        events.add(new OutboxEventEntity(CriticalEventType.RETRY_SCHEDULED, retryEvent));
    }

    /**
     * Registra uma mudança de variável de processo — um evento por variável em {@code variables}, para que
     * o histórico incremental não dependa de esperar o {@code ProcessInstanceFinished} final. Grava o valor
     * bruto: masking é responsabilidade de quem consome o outbox, não deste ponto de construção.
     */
    public void recordProcessVariableChanged(List<OutboxEventEntity> events, String processInstanceId,
                                             String processDefinitionId, String tenantId,
                                             Map<String, ProcessVariable> variables, String actorId) {
        if (!isEnabled() || variables == null || variables.isEmpty()) return;

        Instant changedAt = Instant.now();
        variables.forEach((name, variable) -> events.add(new OutboxEventEntity(CriticalEventType.PROCESS_VARIABLE_CHANGED,
                new ProcessVariableChanged(processInstanceId, processDefinitionId, tenantId, name,
                        variable.isTransient(), variable.value(), actorId, changedAt, false))));
    }

    /**
     * Registra a remoção de variáveis de processo via {@code KikwiflowEngine.unsetVariables} — mesmo evento
     * {@code PROCESS_VARIABLE_CHANGED} de {@link #recordProcessVariableChanged}, mas com {@code removed=true}
     * e sem valor, já que a variável deixa de existir em vez de receber um novo valor.
     */
    public void recordVariablesUnset(List<OutboxEventEntity> events, String processInstanceId,
                                     String processDefinitionId, String tenantId,
                                     Set<String> variableNames, String actorId) {
        if (!isEnabled() || variableNames == null || variableNames.isEmpty()) return;

        Instant changedAt = Instant.now();
        variableNames.forEach(name -> events.add(new OutboxEventEntity(CriticalEventType.PROCESS_VARIABLE_CHANGED,
                new ProcessVariableChanged(processInstanceId, processDefinitionId, tenantId, name,
                        false, null, actorId, changedAt, true))));
    }

    /**
     * Registra o disparo de um timer não-interruptivo que se reagendou (ver o ramo NON_INTERRUPTIVE_TIMER de
     * {@code ContinuationService.handleContinuation}). Timers interruptivos já são cobertos por
     * {@link #recordInterruptedFlowNode} — este método é só para o caso não-interruptivo, que hoje não emitia
     * nenhum evento. {@code actorId} é sempre {@link IdentityContext#system()} — timers disparam
     * automaticamente, nunca por uma ação de usuário.
     */
    public void recordTimerFired(List<OutboxEventEntity> events, ProcessInstanceExecution processInstanceExecution,
                                 String flowNodeDefinitionId, Instant nextDueDate) {
        if (!isEnabled()) return;

        TimerFired timerEvent = new TimerFired(
                processInstanceExecution.getId(),
                processInstanceExecution.getProcessDefinitionId(),
                processInstanceExecution.getTenantId(),
                flowNodeDefinitionId,
                IdentityContext.system().actorId(),
                Instant.now(),
                nextDueDate
        );

        events.add(new OutboxEventEntity(CriticalEventType.TIMER_FIRED, timerEvent));
    }
}
