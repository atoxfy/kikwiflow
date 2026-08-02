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
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
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
        return externalTaskCollection.values().stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId()))
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

        if (unitOfWork.executableTasksToDelete() != null) {
            unitOfWork.executableTasksToDelete().forEach(executableTaskCollection::remove);
        }

        if (unitOfWork.externalTasksToCreate() != null) {
            unitOfWork.externalTasksToCreate().forEach(this::createExternalTask);
        }

        if (unitOfWork.externalTasksToDelete() != null) {
            unitOfWork.externalTasksToDelete().forEach(externalTaskCollection::remove);
        }

        if (unitOfWork.incidentsToCreate() != null) {
            unitOfWork.incidentsToCreate().forEach(i -> incidentCollection.put(i.id(), i));
        }

        if (unitOfWork.incidentsToUpdate() != null) {
            unitOfWork.incidentsToUpdate().forEach(i -> incidentCollection.put(i.id(), i));
        }

        // unitOfWork.incidentsToResolve() (List<String> de ids) não é populado em lugar nenhum do motor
        // hoje — nem mesmo MongoKikwiEngineRepository o trata. Nenhum comportamento é implementado para ele.

        if (unitOfWork.branchPullIntentions() != null) {
            for (BranchPullIntention intention : unitOfWork.branchPullIntentions()) {
                resolveBranchPull(intention);
            }
        }

        if(outboxPersistenceEnabled && unitOfWork.events() != null && !unitOfWork.events().isEmpty()){
            this.outboxEventQueue.addAll(unitOfWork.events());
            this.eventHistory.addAll(unitOfWork.events());
        }
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

    private void resolveBranchPull(BranchPullIntention intention) {
        ExecutableTask joinTask = executableTaskCollection.get(intention.joinTaskId());
        if (joinTask == null) {
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

        ExternalTask updated = ExternalTask.builder()
                .id(task.id())
                .name(task.name())
                .description(task.description())
                .taskDefinitionId(task.taskDefinitionId())
                .processInstanceId(task.processInstanceId())
                .processDefinitionId(task.processDefinitionId())
                .status(task.status())
                .createdAt(task.createdAt())
                .topicName(task.topicName())
                .assignee(assignee)
                .tenantId(task.tenantId())
                .boundaryEvents(task.boundaryEvents())
                .attachedToRefType(task.attachedToRefType())
                .attachedToRefId(task.attachedToRefId())
                .attachedToRefDefinitionId(task.attachedToRefDefinitionId())
                .joinTaskId(task.joinTaskId())
                .build();

        externalTaskCollection.put(externalTaskId, updated);
        writeOutboxEvents(events);
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
            if (!processDefinitionId.equals(t.processDefinitionId())) {
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
                            pi.startedAt(), pi.endedAt(), pi.activeNodes()))
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
