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
package io.kikwiflow.persistence;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.OrphanedChildCompletion;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.exception.OptimisticLockingFailureException;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InMemoryKikwiEngineRepository implements KikwiEngineRepository {

    private final Map<String, ProcessInstance> processInstanceCollection = new HashMap<>();
    private final Map<String, ExecutableTask> executableTaskCollection = new HashMap<>();
    private final Map<String, ExternalTask> externalTaskCollection = new HashMap<>();
    private final Map<String, Incident> incidentCollection = new HashMap<>();
    private final Map<String, ProcessDefinition> processDefinitionsById = new HashMap<>();
    private final Map<String, Map<Integer, ProcessDefinition>> processDefinitionHistoryCollection = new HashMap<>();
    private final Queue<OutboxEventEntity> outboxEventQueue;
    private final List<OutboxEventEntity> eventHistory = new ArrayList<>();
    private final boolean outboxPersistenceEnabled;

    public InMemoryKikwiEngineRepository(Queue<OutboxEventEntity> outboxEventQueue){
        this(outboxEventQueue, true);
    }

    public InMemoryKikwiEngineRepository(Queue<OutboxEventEntity> outboxEventQueue, boolean outboxPersistenceEnabled){
        this.outboxEventQueue = outboxEventQueue;
        this.outboxPersistenceEnabled = outboxPersistenceEnabled;
    }

    public void reset(){
        processInstanceCollection.clear();
        executableTaskCollection.clear();
        externalTaskCollection.clear();
        incidentCollection.clear();
        processDefinitionsById.clear();
        processDefinitionHistoryCollection.clear();
        outboxEventQueue.clear();
        eventHistory.clear();
    }

    /**
     * Helper de conveniência para testes que precisam semear uma {@link ProcessInstance} diretamente,
     * sem passar por {@link #commitWork(UnitOfWork)}. Copia todos os campos do registro recebido.
     */
    public ProcessInstance saveProcessInstance(ProcessInstance instance) {
        ProcessInstance instanceToSave = ProcessInstance.builder()
                .id(null != instance.id() ? instance.id() : UUID.randomUUID().toString())
                .businessKey(instance.businessKey())
                .businessValue(instance.businessValue())
                .tenantId(instance.tenantId())
                .status(instance.status())
                .processDefinitionId(instance.processDefinitionId())
                .variables(instance.variables())
                .startedAt(instance.startedAt())
                .endedAt(instance.endedAt())
                .origin(instance.origin())
                .version(instance.version())
                .parentInstanceId(instance.parentInstanceId())
                .callerTaskId(instance.callerTaskId())
                .callerBranchId(instance.callerBranchId())
                .activeNodes(instance.activeNodes())
                .build();

        this.processInstanceCollection.put(instanceToSave.id(), instanceToSave);
        return instanceToSave;
    }

    @Override
    public List<Incident> findIncidentsByProcessInstanceId(String processInstanceId) {
        return incidentCollection.values().stream()
                .filter(i -> processInstanceId.equals(i.processInstanceId()))
                .toList();
    }

    @Override
    public Optional<Incident> findIncidentById(String incidentId) {
        return Optional.ofNullable(incidentCollection.get(incidentId));
    }

    @Override
    public long countExecutableTasksByDefinitionId(String taskDefinitionId) {
        return executableTaskCollection.values().stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
                .count();
    }

    @Override
    public long countExternalTasksByDefinitionId(String taskDefinitionId) {
        // Exclui CORRELATED — mesma razão do MongoKikwiEngineRepository: uma filha de EVENT_CATCHER GROUP já
        // correlacionada, aguardando só a limpeza em cascata quando a mãe concluir, não é "ativa".
        return externalTaskCollection.values().stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()) && t.status() != ExternalTaskStatus.CORRELATED)
                .count();
    }

    @Override
    public long countOpenIncidentsByProcessDefinition(String processDefinitionId) {
        return incidentCollection.values().stream()
                .filter(i -> processDefinitionId.equals(i.processDefinitionId()) && i.status() == IncidentStatus.OPEN)
                .count();
    }

    @Override
    public long countProcessInstancesByProcessDefinition(String processDefinitionId) {
        return processInstanceCollection.values().stream()
                .filter(p -> processDefinitionId.equals(p.processDefinitionId()))
                .count();
    }

    @Override
    public KKFMetrics getProcessMacroMetrics(String processDefinitionId) {
        long running = processInstanceCollection.values().stream()
                .filter(p -> processDefinitionId.equals(p.processDefinitionId()) && p.status() == ProcessInstanceStatus.ACTIVE)
                .count();
        long failed = countOpenIncidentsByProcessDefinition(processDefinitionId);
        return new KKFMetrics(running, 100.0, failed);
    }

    @Override
    public List<ProcessDefinition> findAProcessDefinitionsByParams(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        Map<Integer, ProcessDefinition> versions = processDefinitionHistoryCollection.get(key);
        if (versions == null) {
            return List.of();
        }
        return versions.values().stream()
                .sorted(Comparator.comparing(ProcessDefinition::version).reversed())
                .toList();
    }

    @Override
    public List<ProcessDefinition> findAllProcessDefinitions() {
        return processDefinitionsById.values().stream()
                .sorted(Comparator.comparing(ProcessDefinition::version).reversed())
                .toList();
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return Optional.ofNullable(this.processInstanceCollection.get(processInstanceId));
    }

    @Override
    public List<ProcessInstance> findProcessInstancesByIdIn(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(processInstanceCollection::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private ExecutableTask createExecutableTask(ExecutableTask executableTask) {
        this.executableTaskCollection.put(executableTask.id(), executableTask);
        return executableTask;
    }

    private ExternalTask createExternalTask(ExternalTask task) {
        this.externalTaskCollection.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<ExternalTask> findExternalTaskById(String externalTaskId) {
        return Optional.ofNullable(externalTaskCollection.get(externalTaskId));
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {
        return externalTaskCollection.values().stream()
            .filter(task -> processInstanceId.equals(task.processInstanceId()))
            .toList();
    }

    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinition){
        // O id/versão/checksum já foram calculados por ProcessDefinitionService antes desta chamada
        // (mesmo contrato que MongoKikwiEngineRepository.saveProcessDefinition — um upsert simples).
        this.processDefinitionsById.put(processDefinition.id(), processDefinition);
        this.addToHistory(processDefinition);
        return processDefinition;
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey){
        return findLatestVersionByKey(processDefinitionKey);
    }

    public void addToHistory(ProcessDefinition processDefinition){
        String key = processDefinition.key();
        Map<Integer, ProcessDefinition> processDefinitionVersionMap = processDefinitionHistoryCollection.get(key);
        if(null == processDefinitionVersionMap){
            processDefinitionVersionMap = new HashMap<>();
        }

        processDefinitionVersionMap.put(processDefinition.version(), processDefinition);
        processDefinitionHistoryCollection.put(key, processDefinitionVersionMap);
    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {

        // Guard de finalização (ver Javadoc de UnitOfWork.finalizingNodeId) — precisa ser a primeiríssima
        // coisa que roda: como este repositório não tem transação real cobrindo o método inteiro (é um duplo
        // de teste single-JVM), "abortar sem escrever nada" só é verdade se o guard falhar antes de qualquer
        // outra mutação. Cobre tanto "pai concluiu normalmente enquanto um boundary event disparava" quanto
        // "dois boundary events interruptivos dispararam ao mesmo tempo": só quem primeiro remover esta
        // entrada específica do mapa vence.
        // cancelledChildEvents só é populado dentro do guard abaixo, quando o nó finalizado é um
        // EXECUTABLE_TASK — única forma de ser um CALL_ACTIVITY_COORDINATOR. Não é gatilhado por
        // executableTasksToDelete genérico (populado em praticamente toda conclusão de tarefa do motor,
        // qualquer tipo) — ver Javadoc de cancelActiveChildSubtrees.
        List<OutboxEventEntity> cancelledChildEvents = new ArrayList<>();

        if (unitOfWork.finalizingNodeId() != null) {
            Object removed = unitOfWork.finalizingNodeType() == AttachedTaskType.EXECUTABLE_TASK
                    ? executableTaskCollection.remove(unitOfWork.finalizingNodeId())
                    : externalTaskCollection.remove(unitOfWork.finalizingNodeId());

            if (removed == null) {
                throw new OptimisticLockingFailureException(
                        "O nó " + unitOfWork.finalizingNodeId() + " já foi finalizado por um evento concorrente (boundary event ou conclusão normal).");
            }

            executableTaskCollection.values().removeIf(t -> unitOfWork.finalizingNodeId().equals(t.attachedToRefId()));
            externalTaskCollection.values().removeIf(t -> unitOfWork.finalizingNodeId().equals(t.attachedToRefId()));

            // Cancelamento recursivo de instâncias filhas já iniciadas — só pode ser relevante quando o nó
            // finalizado é um EXECUTABLE_TASK (um EXTERNAL_TASK nunca é CALL_ACTIVITY_COORDINATOR). Ver
            // docs/engine/20-subprocessos-call-activity-especificacao.md, §5.
            if (unitOfWork.finalizingNodeType() == AttachedTaskType.EXECUTABLE_TASK) {
                cancelActiveChildSubtrees(unitOfWork.finalizingNodeId(), cancelledChildEvents);
            }
        }

        if (unitOfWork.instanceToCreate() != null) {
            ProcessInstance incoming = unitOfWork.instanceToCreate();
            Map<String, Integer> activeNodes = new HashMap<>();
            applyActiveNodeDeltas(activeNodes, unitOfWork.executableTasksToCreate(), unitOfWork.externalTasksToCreate(), null);

            ProcessInstance toStore = ProcessInstance.builder()
                    .id(incoming.id())
                    .businessKey(incoming.businessKey())
                    .businessValue(incoming.businessValue())
                    .tenantId(incoming.tenantId())
                    .status(incoming.status())
                    .processDefinitionId(incoming.processDefinitionId())
                    .variables(incoming.variables())
                    .startedAt(incoming.startedAt())
                    .endedAt(incoming.endedAt())
                    .origin(incoming.origin())
                    .version(incoming.version())
                    .parentInstanceId(incoming.parentInstanceId())
                    .callerTaskId(incoming.callerTaskId())
                    .callerBranchId(incoming.callerBranchId())
                    .activeNodes(activeNodes)
                    .build();

            processInstanceCollection.put(toStore.id(), toStore);
        }

        if (unitOfWork.instanceToUpdate() != null) {
            ProcessInstance incoming = unitOfWork.instanceToUpdate();
            ProcessInstance stored = processInstanceCollection.get(incoming.id());

            if (stored == null) {
                throw new OptimisticLockingFailureException("The instance " + incoming.id() + " was not found for update.");
            }

            Map<String, Integer> activeNodes = new HashMap<>(stored.activeNodes() != null ? stored.activeNodes() : Map.of());
            applyActiveNodeDeltas(activeNodes, unitOfWork.executableTasksToCreate(), unitOfWork.externalTasksToCreate(), unitOfWork.finishedNodeDefinitions());

            ProcessInstance toStore = ProcessInstance.builder()
                    .id(stored.id())
                    .businessKey(stored.businessKey())
                    .businessValue(incoming.businessValue() != null ? incoming.businessValue() : stored.businessValue())
                    .tenantId(stored.tenantId())
                    .status(incoming.status() != null ? incoming.status() : stored.status())
                    .processDefinitionId(stored.processDefinitionId())
                    .variables(incoming.variables() != null ? incoming.variables() : stored.variables())
                    .startedAt(stored.startedAt())
                    .endedAt(incoming.endedAt() != null ? incoming.endedAt() : stored.endedAt())
                    .origin(stored.origin())
                    .version(stored.version() + 1)
                    .parentInstanceId(stored.parentInstanceId())
                    .callerTaskId(stored.callerTaskId())
                    .callerBranchId(stored.callerBranchId())
                    .activeNodes(activeNodes)
                    .build();

            processInstanceCollection.put(toStore.id(), toStore);
        }

        if (unitOfWork.instanceToDelete() != null) {
            String instanceId = unitOfWork.instanceToDelete().id();
            removeInstanceAndTasks(instanceId);
            // Decisão consciente (mesma do MongoKikwiEngineRepository): incidentes e eventos de outbox
            // não são removidos — outbox_events/incidentCollection dobram como histórico durável.
        }

        if (unitOfWork.executableTasksToCreate() != null) {
            unitOfWork.executableTasksToCreate().forEach(this::createExecutableTask);
        }

        if (unitOfWork.executableTasksToUpdate() != null) {
            unitOfWork.executableTasksToUpdate().forEach(t -> executableTaskCollection.put(t.id(), t));
        }

        if (unitOfWork.executableTasksToDelete() != null && !unitOfWork.executableTasksToDelete().isEmpty()) {
            unitOfWork.executableTasksToDelete().forEach(executableTaskCollection::remove);
            // Cascata análoga à de ExternalTask/coordinatorTaskId acima, mas para CALL_ACTIVITY_STARTER:
            // quando a coordenadora é apagada (timeout do boundary event na coordenadora — ver
            // docs/engine/20-subprocessos-call-activity-especificacao.md, §5), qualquer iniciadora ainda
            // pendente (que aponta para a coordenadora via joinTaskId) também é removida. Escopo restrito ao
            // tipo CALL_ACTIVITY_STARTER (não um cascade genérico por joinTaskId) para não arriscar tocar
            // ramificações de PARALLEL_GATEWAY/JOIN_GATEWAY ainda em andamento.
            executableTaskCollection.values().removeIf(t ->
                    t.type() == ExecutableTaskType.CALL_ACTIVITY_STARTER
                            && t.joinTaskId() != null
                            && unitOfWork.executableTasksToDelete().contains(t.joinTaskId()));
        }

        if (unitOfWork.externalTasksToCreate() != null) {
            unitOfWork.externalTasksToCreate().forEach(this::createExternalTask);
        }

        if (unitOfWork.externalTasksToDelete() != null && !unitOfWork.externalTasksToDelete().isEmpty()) {
            unitOfWork.externalTasksToDelete().forEach(externalTaskCollection::remove);
            // Cascata genérica: qualquer ExternalTask filha (EVENT_CATCHER GROUP) cujo coordinatorTaskId
            // aponte para uma tarefa apagada nesta mesma transação também é removida — cobre timeout de
            // boundary timer na mãe e limpeza de irmãs remanescentes na política ANY, sem lógica específica
            // de EVENT_CATCHER em ContinuationService.
            externalTaskCollection.values().removeIf(t ->
                    t.coordinatorTaskId() != null && unitOfWork.externalTasksToDelete().contains(t.coordinatorTaskId()));
        }

        if (unitOfWork.incidentsToCreate() != null) {
            unitOfWork.incidentsToCreate().forEach(i -> incidentCollection.put(i.id(), i));
        }

        if (unitOfWork.incidentsToUpdate() != null) {
            unitOfWork.incidentsToUpdate().forEach(i -> incidentCollection.put(i.id(), i));
        }

        // unitOfWork.incidentsToResolve() (List<String> de ids) não é populado em lugar nenhum do motor
        // hoje — nem mesmo MongoKikwiEngineRepository o trata. Nenhum comportamento é implementado para ele.

        List<OutboxEventEntity> orphanEvents = new ArrayList<>();
        if (unitOfWork.branchPullIntentions() != null) {
            for (BranchPullIntention intention : unitOfWork.branchPullIntentions()) {
                resolveBranchPull(intention, unitOfWork, orphanEvents);
            }
        }

        List<OutboxEventEntity> allEvents = new ArrayList<>();
        if (unitOfWork.events() != null) {
            allEvents.addAll(unitOfWork.events());
        }
        allEvents.addAll(orphanEvents);
        allEvents.addAll(cancelledChildEvents);

        if (outboxPersistenceEnabled && !allEvents.isEmpty()) {
            this.outboxEventQueue.addAll(allEvents);
            this.eventHistory.addAll(allEvents);
        }
    }

    /**
     * Cancelamento recursivo de subárvore(s) de {@code ProcessInstance} spawnada(s) por uma coordenadora
     * {@code CALL_ACTIVITY_COORDINATOR} apagada nesta transação via o guard de finalização (ver
     * docs/engine/20-subprocessos-call-activity-especificacao.md, §5). BFS em duas fases:
     * <p>
     * 1. Nível 0: toda instância ACTIVE cujo {@code callerTaskId} é {@code coordinatorTaskId} — os filhos
     * diretos desta coordenadora especificamente ({@code callerTaskId} escopa por coordenadora, nunca confunde
     * call activities irmãs no mesmo processo).
     * <p>
     * 2. Níveis seguintes: toda instância ACTIVE cujo {@code parentInstanceId} está no nível anterior — cobre
     * netos/bisnetos de call activities aninhadas dentro do filho, independente de qual coordenadora interna os
     * gerou.
     * <p>
     * Cada instância cancelada tem suas tasks apagadas (mesmo escopo de {@link #removeInstanceAndTasks}),
     * a própria linha apagada (nunca mantida com {@code status=CANCELLED} — a coleção de runtime é estado
     * operacional, não histórico, mesma decisão já aplicada a {@code instanceToDelete}), e um evento
     * {@code PROCESS_INSTANCE_FINISHED} com {@code status=CANCELLED} é acumulado em {@code out} para entrar no
     * mesmo commit — nunca a exclusão sem o evento correspondente. Incidentes não são tocados (histórico).
     * <p>
     * <b>Nota de performance</b> (relevante sobretudo no equivalente Mongo, {@code MongoKikwiEngineRepository}):
     * o chamador só invoca este método dentro do guard de {@code UnitOfWork.finalizingNodeId} — nunca a partir
     * de {@code executableTasksToDelete} genérico, que é populado em praticamente toda conclusão de
     * {@code ExecutableTask} do motor (qualquer tipo, não só coordenadora). {@code finalizingNodeId} só existe
     * para finalizações por boundary event, ordens de magnitude mais raro.
     */
    private void cancelActiveChildSubtrees(String coordinatorTaskId, List<OutboxEventEntity> out) {
        List<String> frontier = processInstanceCollection.values().stream()
                .filter(pi -> pi.status() == ProcessInstanceStatus.ACTIVE
                        && coordinatorTaskId.equals(pi.callerTaskId()))
                .map(ProcessInstance::id)
                .toList();

        while (!frontier.isEmpty()) {
            List<String> nextFrontier = new ArrayList<>();

            for (String instanceId : frontier) {
                ProcessInstance instance = processInstanceCollection.get(instanceId);
                if (instance == null) {
                    continue;
                }

                nextFrontier.addAll(processInstanceCollection.values().stream()
                        .filter(pi -> pi.status() == ProcessInstanceStatus.ACTIVE && instanceId.equals(pi.parentInstanceId()))
                        .map(ProcessInstance::id)
                        .toList());

                out.add(buildCancelledEvent(instance));
                removeInstanceAndTasks(instanceId);
            }

            frontier = nextFrontier;
        }
    }

    private OutboxEventEntity buildCancelledEvent(ProcessInstance instance) {
        ProcessInstanceFinished event = ProcessInstanceFinished.builder()
                .id(instance.id())
                .businessKey(instance.businessKey())
                .status(ProcessInstanceStatus.CANCELLED)
                .processDefinitionId(instance.processDefinitionId())
                .variables(instance.variables())
                .startedAt(instance.startedAt())
                .endedAt(Instant.now())
                .businessValue(instance.businessValue())
                .tenantId(instance.tenantId())
                .origin(instance.origin())
                .parentInstanceId(instance.parentInstanceId())
                .callerTaskId(instance.callerTaskId())
                .callerBranchId(instance.callerBranchId())
                .build();

        return new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_FINISHED, event);
    }

    /**
     * Replica a lógica de delta de {@code activeNodes} do MongoKikwiEngineRepository: +1 por tarefa nova
     * criada nesta mesma transação, -1 por nó de fluxo finalizado. Esse campo nunca vem preenchido pelo
     * {@code ProcessInstanceExecution} — é responsabilidade da camada de persistência calculá-lo.
     */
    private void applyActiveNodeDeltas(Map<String, Integer> activeNodes,
                                        List<ExecutableTask> executableTasksToCreate,
                                        List<ExternalTask> externalTasksToCreate,
                                        List<String> finishedNodeDefinitions) {
        if (finishedNodeDefinitions != null) {
            for (String nodeId : finishedNodeDefinitions) {
                activeNodes.merge(nodeId, -1, Integer::sum);
            }
        }
        if (executableTasksToCreate != null) {
            for (ExecutableTask t : executableTasksToCreate) {
                activeNodes.merge(t.taskDefinitionId(), 1, Integer::sum);
            }
        }
        if (externalTasksToCreate != null) {
            for (ExternalTask t : externalTasksToCreate) {
                activeNodes.merge(t.taskDefinitionId(), 1, Integer::sum);
            }
        }
    }

    /**
     * {@code orphanEvents} recebe um {@link OrphanedChildCompletion} (ver
     * docs/engine/20-subprocessos-call-activity-especificacao.md, §4.4) quando {@code joinTaskId} não é
     * encontrado — cenário normal apenas para {@code CALL_ACTIVITY_COORDINATOR}: a coordenadora já foi
     * apagada (timeout do boundary event) antes de um filho tentar liberar sua branch. A identidade da
     * instância filha vem do próprio {@code unitOfWork} (é o commit da sua própria conclusão): {@code
     * instanceToDelete} quando ela completou, {@code instanceToUpdate} nos demais casos.
     */
    private void resolveBranchPull(BranchPullIntention intention, UnitOfWork unitOfWork, List<OutboxEventEntity> orphanEvents) {
        ExecutableTask joinTask = executableTaskCollection.get(intention.joinTaskId());
        if (joinTask == null) {
            ProcessInstance childInstance = unitOfWork.instanceToDelete() != null
                    ? unitOfWork.instanceToDelete()
                    : unitOfWork.instanceToUpdate();

            orphanEvents.add(new OutboxEventEntity(CriticalEventType.ORPHANED_CHILD_COMPLETION,
                    new OrphanedChildCompletion(
                            childInstance != null ? childInstance.id() : null,
                            childInstance != null ? childInstance.processDefinitionId() : null,
                            childInstance != null ? childInstance.tenantId() : null,
                            childInstance != null ? childInstance.parentInstanceId() : null,
                            intention.joinTaskId(),
                            intention.branchId(),
                            Instant.now())));
            return;
        }

        List<String> remaining = new ArrayList<>(joinTask.pendingBranchIds() != null ? joinTask.pendingBranchIds() : List.of());
        remaining.remove(intention.branchId());

        ExecutableTask.Builder builder = joinTask.toBuilder().pendingBranchIds(remaining);
        if (remaining.isEmpty()) {
            builder.status(ExecutableTaskStatus.PENDING);
        }

        executableTaskCollection.put(joinTask.id(), builder.build());
    }

    private void removeInstanceAndTasks(String instanceId) {
        processInstanceCollection.remove(instanceId);
        executableTaskCollection.values().removeIf(t -> instanceId.equals(t.processInstanceId()));
        externalTaskCollection.values().removeIf(t -> instanceId.equals(t.processInstanceId()));
    }

    @Override
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId, long lockTimeoutMillis) {
        Instant lockExpirationThreshold = now.minusMillis(lockTimeoutMillis);

        List<ExecutableTask> candidates = this.executableTaskCollection.values().stream()
                .filter(task -> isDuePending(task, now) || isStuckLocked(task, lockExpirationThreshold))
                .sorted(Comparator.comparing(task -> task.dueDate() == null ? Instant.MIN : task.dueDate()))
                .limit(limit)
                .toList();

        List<ExecutableTask> lockedTasks = new ArrayList<>();
        for (ExecutableTask candidate : candidates){
            ExecutableTask lockedTask = candidate.toBuilder()
                    .status(ExecutableTaskStatus.LOCKED)
                    .executorId(workerId)
                    .acquiredAt(Instant.now())
                    .build();
            this.executableTaskCollection.put(candidate.id(), lockedTask);
            lockedTasks.add(lockedTask);
        }

        return lockedTasks;
    }

    private boolean isDuePending(ExecutableTask task, Instant now) {
        return task.status() == ExecutableTaskStatus.PENDING
                && (task.dueDate() == null || !task.dueDate().isAfter(now));
    }

    private boolean isStuckLocked(ExecutableTask task, Instant lockExpirationThreshold) {
        return task.status() == ExecutableTaskStatus.LOCKED
                && task.acquiredAt() != null
                && !task.acquiredAt().isAfter(lockExpirationThreshold);
    }

    @Override
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables, List<OutboxEventEntity> events) {
        ProcessInstance stored = processInstanceCollection.get(processInstanceId);
        if (stored == null) {
            return null;
        }
        if (variables == null || variables.isEmpty()) {
            return stored;
        }

        Map<String, ProcessVariable> merged = new HashMap<>(stored.variables() != null ? stored.variables() : Map.of());
        merged.putAll(variables);

        ProcessInstance updated = ProcessInstance.builder()
                .id(stored.id())
                .businessKey(stored.businessKey())
                .businessValue(stored.businessValue())
                .tenantId(stored.tenantId())
                .status(stored.status())
                .processDefinitionId(stored.processDefinitionId())
                .variables(merged)
                .startedAt(stored.startedAt())
                .endedAt(stored.endedAt())
                .origin(stored.origin())
                .version(stored.version())
                .parentInstanceId(stored.parentInstanceId())
                .callerTaskId(stored.callerTaskId())
                .callerBranchId(stored.callerBranchId())
                .activeNodes(stored.activeNodes())
                .build();

        processInstanceCollection.put(processInstanceId, updated);
        writeOutboxEvents(events);
        return updated;
    }

    @Override
    public ProcessInstance unsetVariables(String processInstanceId, Set<String> variableNames, List<OutboxEventEntity> events) {
        ProcessInstance stored = processInstanceCollection.get(processInstanceId);
        if (stored == null) {
            return null;
        }
        if (variableNames == null || variableNames.isEmpty()) {
            return stored;
        }

        Map<String, ProcessVariable> remaining = new HashMap<>(stored.variables() != null ? stored.variables() : Map.of());
        variableNames.forEach(remaining::remove);

        ProcessInstance updated = ProcessInstance.builder()
                .id(stored.id())
                .businessKey(stored.businessKey())
                .businessValue(stored.businessValue())
                .tenantId(stored.tenantId())
                .status(stored.status())
                .processDefinitionId(stored.processDefinitionId())
                .variables(remaining)
                .startedAt(stored.startedAt())
                .endedAt(stored.endedAt())
                .origin(stored.origin())
                .version(stored.version())
                .parentInstanceId(stored.parentInstanceId())
                .callerTaskId(stored.callerTaskId())
                .callerBranchId(stored.callerBranchId())
                .activeNodes(stored.activeNodes())
                .build();

        processInstanceCollection.put(processInstanceId, updated);
        writeOutboxEvents(events);
        return updated;
    }

    @Override
    public void claim(String externalTaskId, String assignee, List<OutboxEventEntity> events) {
        withAssignee(externalTaskId, assignee, events);
    }

    @Override
    public void unclaim(String externalTaskId, List<OutboxEventEntity> events) {
        withAssignee(externalTaskId, null, events);
    }

    private void withAssignee(String externalTaskId, String assignee, List<OutboxEventEntity> events) {
        ExternalTask task = externalTaskCollection.get(externalTaskId);
        if (task == null) {
            return;
        }

        // toBuilder() preserva todos os campos (inclui os de EVENT_CATCHER) — a reconstrução manual anterior
        // (campo a campo) já vinha silenciosamente descartando branchId/pendingBranchIds antes desta mudança.
        ExternalTask updated = task.toBuilder().assignee(assignee).build();

        externalTaskCollection.put(externalTaskId, updated);
        writeOutboxEvents(events);
    }

    @Override
    public Optional<ExternalTask> findExternalTaskByCorrelationKey(String correlationKey, String tenantId) {
        // status=CREATED exclui tarefas já correlacionadas (CORRELATED) — uma segunda correlação para a
        // mesma chave não encontra nada, preservando a proteção de idempotência sem apagar a linha.
        return externalTaskCollection.values().stream()
                .filter(t -> correlationKey.equals(t.correlationKey())
                        && Objects.equals(tenantId, t.tenantId())
                        && t.status() == ExternalTaskStatus.CREATED)
                .findFirst();
    }

    @Override
    public boolean resolveCorrelationChild(String childTaskId, String parentTaskId, MatchPolicy matchPolicy) {
        ExternalTask child = externalTaskCollection.get(childTaskId);
        String correlationKey = child != null ? child.correlationKey() : null;

        // Não apaga a filha aqui — só marca CORRELATED. A limpeza real acontece via cascata por
        // coordinatorTaskId em commitWork, junto com a tarefa-mãe (ver ExternalTaskStatus.CORRELATED).
        if (child != null) {
            externalTaskCollection.put(childTaskId, child.toBuilder().status(ExternalTaskStatus.CORRELATED).build());
        }

        ExternalTask parent = externalTaskCollection.get(parentTaskId);
        if (parent == null) {
            // mãe já não existe mais (ex.: corrida perdida contra outra chave em ANY, ou timeout de boundary
            // timer) — nada a satisfazer.
            return false;
        }

        if (matchPolicy == MatchPolicy.ANY) {
            if (parent.status() == ExternalTaskStatus.CREATED) {
                externalTaskCollection.put(parentTaskId, parent.toBuilder().status(ExternalTaskStatus.COMPLETED).build());
                return true;
            }
            return false;
        }

        // ALL
        List<String> remaining = new ArrayList<>(parent.pendingCorrelationKeys() != null ? parent.pendingCorrelationKeys() : List.of());
        remaining.remove(correlationKey);

        ExternalTask updatedParent = parent.toBuilder().pendingCorrelationKeys(remaining).build();
        externalTaskCollection.put(parentTaskId, updatedParent);

        return remaining.isEmpty();
    }

    /**
     * Espelha o trecho de {@code commitWork} que grava eventos no outbox — usado pelos comandos que não
     * passam por {@link UnitOfWork} ({@code claim}/{@code unclaim}/{@code addVariables}), que hoje mutam
     * estado diretamente em vez de acumular num {@code UnitOfWork}.
     */
    private void writeOutboxEvents(List<OutboxEventEntity> events) {
        if (outboxPersistenceEnabled && events != null && !events.isEmpty()) {
            this.outboxEventQueue.addAll(events);
            this.eventHistory.addAll(events);
        }
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        removeInstanceAndTasks(processInstanceId);
        // Mesma decisão consciente de MongoKikwiEngineRepository.deleteProcessInstanceById: incidentes e
        // eventos de outbox não são apagados.
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        return Optional.ofNullable(processDefinitionsById.get(processDefinitionId));
    }

    @Override
    public Optional<ExecutableTask> findExecutableTaskById(String executableTaskId) {
        return Optional.ofNullable(this.executableTaskCollection.get(executableTaskId));
    }

    @Override
    public Optional<ExecutableTask> findAndGetFirstPendingExecutableTask(String id) {
        return executableTaskCollection.values()
                .stream()
                .filter(task -> task.status() == ExecutableTaskStatus.PENDING)
                .findFirst();
    }

    @Override
    public List<ProcessInstance> findProcessInstanceByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return processInstanceCollection.values()
                .stream()
                .filter(p -> p.processDefinitionId().equals(processDefinitionId) && Objects.equals(tenantId, p.tenantId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> t.processDefinitionId().equals(processDefinitionId) && Objects.equals(tenantId, t.tenantId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId) {
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> processDefinitionId.equals(t.processDefinitionId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, List<String> tenantIds) {
        Set<String> tenantIdSet = tenantIds != null ? new HashSet<>(tenantIds) : Set.of();
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> processDefinitionId.equals(t.processDefinitionId()) && tenantIdSet.contains(t.tenantId()))
                .toList();
    }

    @Override
    public List<ExternalTask> findExternalTasksByAssignee(String assignee, String tenantId) {
        return this.externalTaskCollection.values()
                .stream()
                .filter(t -> Objects.equals(assignee, t.assignee()) && Objects.equals(tenantId, t.tenantId()))
                .toList();
    }

    @Override
    public ExternalTaskQuery createExternalTaskQuery() {
        return new InMemoryExternalTaskQuery();
    }

    @Override
    public ProcessInstanceQuery createProcessInstanceQuery() {
        return new InMemoryProcessInstanceQuery();
    }

    @Override
    public Map<String, KKFMetrics> getMetricsByNodeForProcessDefinition(String processDefinitionId) {
        Map<String, KKFMetrics> result = new HashMap<>();

        Map<String, long[]> executableAgg = new HashMap<>();
        for (ExecutableTask t : executableTaskCollection.values()) {
            if (!processDefinitionId.equals(t.processDefinitionId())) {
                continue;
            }
            long[] agg = executableAgg.computeIfAbsent(t.taskDefinitionId(), k -> new long[2]);
            agg[0]++;
            if (t.status() == ExecutableTaskStatus.ERROR) {
                agg[1]++;
            }
        }
        executableAgg.forEach((nodeId, agg) -> result.put(nodeId, new KKFMetrics(agg[0], 100.0, agg[1])));

        Map<String, Long> externalAgg = new HashMap<>();
        for (ExternalTask t : externalTaskCollection.values()) {
            if (!processDefinitionId.equals(t.processDefinitionId()) || t.status() == ExternalTaskStatus.CORRELATED) {
                continue;
            }
            externalAgg.merge(t.taskDefinitionId(), 1L, Long::sum);
        }
        externalAgg.forEach((nodeId, running) -> result.put(nodeId, new KKFMetrics(running, 100.0, 0L)));

        return result;
    }

    @Override
    public Optional<ProcessDefinition> findByKeyAndChecksum(String key, String checksum) {
        Map<Integer, ProcessDefinition> versions = processDefinitionHistoryCollection.get(key);
        if (versions == null) {
            return Optional.empty();
        }
        return versions.values().stream()
                .filter(pd -> Objects.equals(pd.checksum(), checksum))
                .findFirst();
    }

    @Override
    public Optional<ProcessDefinition> findLatestVersionByKey(String key) {
        Map<Integer, ProcessDefinition> versions = processDefinitionHistoryCollection.get(key);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.values().stream().max(Comparator.comparing(ProcessDefinition::version));
    }

    @Override
    public List<ExecutableTask> findExecutableTasksByProcessInstanceId(String processInstanceId) {
        return executableTaskCollection.values().stream()
                .filter(t -> processInstanceId.equals(t.processInstanceId()))
                .toList();
    }

    @Override
    public List<OutboxEventEntity> findEventHistoryByProcessInstanceId(String processInstanceId) {
        return eventHistory.stream()
                .filter(entity -> entity.getPayload() != null && processInstanceId.equals(entity.getPayload().processInstanceId()))
                .sorted(Comparator.comparing(OutboxEventEntity::getTimestamp))
                .toList();
    }

    @Override
    public void ensureIndexes() {
        // Sem índices no backend in-memory.
    }

    /**
     * Espelha o conjunto de filtros de {@code MongoExternalTaskQuery}, via {@link Predicate} em vez de Bson.
     */
    private class InMemoryExternalTaskQuery implements ExternalTaskQuery {
        private final List<Predicate<ExternalTask>> predicates = new ArrayList<>();

        @Override
        public ExternalTaskQuery tenantId(String tenantId) {
            if (tenantId != null) {
                predicates.add(t -> tenantId.equals(t.tenantId()));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery taskDefinitionId(String taskDefinitionId) {
            if (taskDefinitionId != null) {
                predicates.add(t -> taskDefinitionId.equals(t.taskDefinitionId()));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery tenantIdIn(List<String> tenantIdIn) {
            if (tenantIdIn != null && !tenantIdIn.isEmpty()) {
                Set<String> tenantIds = new HashSet<>(tenantIdIn);
                predicates.add(t -> tenantIds.contains(t.tenantId()));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery processInstanceId(String processInstanceId) {
            if (processInstanceId != null) {
                predicates.add(t -> processInstanceId.equals(t.processInstanceId()));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery processDefinitionId(String processDefinitionId) {
            if (processDefinitionId != null) {
                predicates.add(t -> processDefinitionId.equals(t.processDefinitionId()));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery assignee(String assignee) {
            if (assignee != null) {
                predicates.add(t -> assignee.equals(t.assignee()));
            }
            return this;
        }

        @Override
        public List<ExternalTask> list() {
            return externalTaskCollection.values().stream().filter(this::matches).toList();
        }

        @Override
        public long count() {
            return externalTaskCollection.values().stream().filter(this::matches).count();
        }

        private boolean matches(ExternalTask task) {
            return predicates.stream().allMatch(p -> p.test(task));
        }
    }

    /**
     * Espelha o conjunto de filtros de {@code MongoProcessInstanceQuery}, via {@link Predicate} em vez de Bson.
     * {@code orderBy} suporta um subconjunto pragmático de campos ordenáveis — Java não tem acesso dinâmico a
     * campo por nome como o driver Mongo tem.
     */
    private class InMemoryProcessInstanceQuery implements ProcessInstanceQuery {
        private final List<Predicate<ProcessInstance>> predicates = new ArrayList<>();
        private int page = 0;
        private int size = 20;
        private String orderByField = "startedAt";
        private boolean ascending = false;

        @Override
        public ProcessInstanceQuery processDefinitionId(String processDefinitionId) {
            if (processDefinitionId != null && !processDefinitionId.isBlank()) {
                predicates.add(pi -> processDefinitionId.equals(pi.processDefinitionId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery processDefinitionIdIn(List<String> processDefinitionIds) {
            if (processDefinitionIds != null && !processDefinitionIds.isEmpty()) {
                Set<String> ids = new HashSet<>(processDefinitionIds);
                predicates.add(pi -> ids.contains(pi.processDefinitionId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery processDefinitionKeyIn(List<String> processDefinitionKeys) {
            if (processDefinitionKeys != null && !processDefinitionKeys.isEmpty()) {
                Set<String> keys = new HashSet<>(processDefinitionKeys);
                Set<String> resolvedIds = processDefinitionsById.values().stream()
                        .filter(pd -> keys.contains(pd.key()))
                        .map(ProcessDefinition::id)
                        .collect(Collectors.toSet());
                predicates.add(pi -> resolvedIds.contains(pi.processDefinitionId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery activeNodeId(String activeNodeId) {
            if (activeNodeId != null && !activeNodeId.isBlank()) {
                predicates.add(pi -> pi.activeNodes() != null && pi.activeNodes().getOrDefault(activeNodeId, 0) > 0);
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery parentInstanceId(String parentInstanceId) {
            if (parentInstanceId != null && !parentInstanceId.isBlank()) {
                predicates.add(pi -> parentInstanceId.equals(pi.parentInstanceId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery tenantId(String tenantId) {
            if (tenantId != null && !tenantId.isBlank()) {
                predicates.add(pi -> tenantId.equals(pi.tenantId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery tenantIdIn(List<String> tenantIds) {
            if (tenantIds != null && !tenantIds.isEmpty()) {
                Set<String> ids = new HashSet<>(tenantIds);
                predicates.add(pi -> ids.contains(pi.tenantId()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery statusIn(List<ProcessInstanceStatus> statuses) {
            if (statuses != null && !statuses.isEmpty()) {
                Set<ProcessInstanceStatus> statusSet = new HashSet<>(statuses);
                predicates.add(pi -> statusSet.contains(pi.status()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery businessKey(String businessKey) {
            if (businessKey != null && !businessKey.isBlank()) {
                predicates.add(pi -> businessKey.equals(pi.businessKey()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery businessKeyIn(List<String> businessKeys) {
            if (businessKeys != null && !businessKeys.isEmpty()) {
                Set<String> keys = new HashSet<>(businessKeys);
                predicates.add(pi -> keys.contains(pi.businessKey()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery startedAfter(Instant startedAfter) {
            if (startedAfter != null) {
                predicates.add(pi -> pi.startedAt() != null && !pi.startedAt().isBefore(startedAfter));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery startedBefore(Instant startedBefore) {
            if (startedBefore != null) {
                predicates.add(pi -> pi.startedAt() != null && !pi.startedAt().isAfter(startedBefore));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery variableEquals(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                predicates.add(pi -> pi.variables() != null
                        && pi.variables().containsKey(key)
                        && pi.variables().get(key) != null
                        && value.equals(pi.variables().get(key).value()));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery variableExists(String key) {
            if (key != null && !key.isBlank()) {
                predicates.add(pi -> pi.variables() != null && pi.variables().containsKey(key));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery orderBy(String field, boolean ascending) {
            if (field != null && !field.isBlank()) {
                this.orderByField = field;
                this.ascending = ascending;
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery page(int page) {
            this.page = Math.max(0, page);
            return this;
        }

        @Override
        public ProcessInstanceQuery size(int size) {
            this.size = size > 0 ? size : 20;
            return this;
        }

        @Override
        public PageResult<ProcessInstanceSummary> listSummary() {
            Comparator<ProcessInstance> comparator = comparatorFor(orderByField);
            if (!ascending) {
                comparator = comparator.reversed();
            }

            List<ProcessInstance> matched = processInstanceCollection.values().stream()
                    .filter(this::matches)
                    .sorted(comparator)
                    .toList();

            long totalElements = matched.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            List<ProcessInstanceSummary> content = matched.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .map(pi -> new ProcessInstanceSummary(
                            pi.id(), pi.businessKey(), pi.status(), pi.processDefinitionId(),
                            pi.startedAt(), pi.endedAt(), pi.activeNodes(),
                            pi.parentInstanceId(), pi.callerTaskId(), pi.callerBranchId()))
                    .toList();

            return new PageResult<>(content, totalElements, totalPages, page, size);
        }

        private boolean matches(ProcessInstance pi) {
            return predicates.stream().allMatch(p -> p.test(pi));
        }

        private Comparator<ProcessInstance> comparatorFor(String field) {
            return switch (field) {
                case "endedAt" -> Comparator.comparing(ProcessInstance::endedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
                case "businessKey" -> Comparator.comparing(ProcessInstance::businessKey, Comparator.nullsFirst(Comparator.naturalOrder()));
                case "id" -> Comparator.comparing(ProcessInstance::id);
                default -> Comparator.comparing(ProcessInstance::startedAt, Comparator.nullsFirst(Comparator.naturalOrder()));
            };
        }
    }
}
