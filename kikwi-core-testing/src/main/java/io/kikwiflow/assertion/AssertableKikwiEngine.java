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

package io.kikwiflow.assertion;

import io.kikwiflow.history.repository.FlowNodeExecutionSnapshotInMemoryRepository;
import io.kikwiflow.history.repository.ProcessInstanceInMemorySnapshotRepository;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

public class AssertableKikwiEngine implements KikwiEngineRepository {

    private final Queue<OutboxEventEntity> outboxEventQueue = new ConcurrentLinkedQueue<OutboxEventEntity>();
    private final AssertableEventListener assertableEventListener;
    private final InMemoryKikwiEngineRepository inMemoryKikwiEngineRepository;
    private final FlowNodeExecutionSnapshotInMemoryRepository flowNodeExecutionSnapshotRepository;
    private final ProcessInstanceInMemorySnapshotRepository processInstanceSnapshotRepository;

    public AssertableKikwiEngine(){
        this.processInstanceSnapshotRepository = new ProcessInstanceInMemorySnapshotRepository();
        this.flowNodeExecutionSnapshotRepository = new FlowNodeExecutionSnapshotInMemoryRepository();
        this.inMemoryKikwiEngineRepository = spy(new InMemoryKikwiEngineRepository(outboxEventQueue));
        this.assertableEventListener = spy(new AssertableEventListener(outboxEventQueue, flowNodeExecutionSnapshotRepository, processInstanceSnapshotRepository));
    }

    public ProcessInstance saveProcessInstance(ProcessInstance instance) {
        return inMemoryKikwiEngineRepository.saveProcessInstance(instance);
    }

    @Override
    public List<Incident> findIncidentsByProcessInstanceId(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findIncidentsByProcessInstanceId(processInstanceId);
    }

    @Override
    public Optional<Incident> findIncidentById(String incidentId) {
        return inMemoryKikwiEngineRepository.findIncidentById(incidentId);
    }

    @Override
    public long countExecutableTasksByDefinitionId(String taskDefinitionId) {
        return inMemoryKikwiEngineRepository.countExecutableTasksByDefinitionId(taskDefinitionId);
    }

    @Override
    public long countExternalTasksByDefinitionId(String taskDefinitionId) {
        return inMemoryKikwiEngineRepository.countExternalTasksByDefinitionId(taskDefinitionId);
    }

    @Override
    public long countOpenIncidentsByProcessDefinition(String processDefinitionId) {
        return inMemoryKikwiEngineRepository.countOpenIncidentsByProcessDefinition(processDefinitionId);
    }

    @Override
    public long countProcessInstancesByProcessDefinition(String processDefinitionId) {
        return inMemoryKikwiEngineRepository.countProcessInstancesByProcessDefinition(processDefinitionId);
    }

    @Override
    public KKFMetrics getProcessMacroMetrics(String processDefinitionId) {
        return inMemoryKikwiEngineRepository.getProcessMacroMetrics(processDefinitionId);
    }

    @Override
    public List<ProcessDefinition> findAProcessDefinitionsByParams(String key) {
        return inMemoryKikwiEngineRepository.findAProcessDefinitionsByParams(key);
    }

    @Override
    public List<ProcessDefinition> findAllProcessDefinitions() {
        return inMemoryKikwiEngineRepository.findAllProcessDefinitions();
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
    }

    @Override
    public List<ProcessInstance> findProcessInstancesByIdIn(List<String> ids) {
        return inMemoryKikwiEngineRepository.findProcessInstancesByIdIn(ids);
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
    }

    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy) {
        return inMemoryKikwiEngineRepository.saveProcessDefinition(processDefinitionDeploy);
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey) {
        return inMemoryKikwiEngineRepository.findProcessDefinitionByKey(processDefinitionKey);
    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        this.inMemoryKikwiEngineRepository.commitWork(unitOfWork);
    }

    @Override
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId, long lockTimeoutMillis) {
        return this.inMemoryKikwiEngineRepository.findAndLockDueTasks(now, limit, workerId, lockTimeoutMillis);
    }

    @Override
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables, List<OutboxEventEntity> events) {
        return inMemoryKikwiEngineRepository.addVariables(processInstanceId, variables, events);
    }

    @Override
    public ProcessInstance unsetVariables(String processInstanceId, Set<String> variableNames, List<OutboxEventEntity> events) {
        return inMemoryKikwiEngineRepository.unsetVariables(processInstanceId, variableNames, events);
    }

    @Override
    public void claim(String externalTaskId, String assignee, List<OutboxEventEntity> events) {
        this.inMemoryKikwiEngineRepository.claim(externalTaskId, assignee, events);
    }

    @Override
    public void unclaim(String externalTaskId, List<OutboxEventEntity> events) {
        this.inMemoryKikwiEngineRepository.unclaim(externalTaskId, events);
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        inMemoryKikwiEngineRepository.deleteProcessInstanceById(processInstanceId);
    }

    @Override
    public Optional<ExternalTask> findExternalTaskByCorrelationKey(String correlationKey, String tenantId) {
        return inMemoryKikwiEngineRepository.findExternalTaskByCorrelationKey(correlationKey, tenantId);
    }

    @Override
    public boolean resolveCorrelationChild(String childTaskId, String parentTaskId, MatchPolicy matchPolicy) {
        return inMemoryKikwiEngineRepository.resolveCorrelationChild(childTaskId, parentTaskId, matchPolicy);
    }

    @Override
    public Optional<ExternalTask> findExternalTaskById(String externalTaskId) {
        return inMemoryKikwiEngineRepository.findExternalTaskById(externalTaskId);
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        return inMemoryKikwiEngineRepository.findProcessDefinitionById(processDefinitionId);
    }

    @Override
    public Optional<ExecutableTask> findExecutableTaskById(String executableTaskId) {
        return inMemoryKikwiEngineRepository.findExecutableTaskById(executableTaskId);
    }

    @Override
    public Optional<ExecutableTask> findAndGetFirstPendingExecutableTask(String id) {
        return this.inMemoryKikwiEngineRepository.findAndGetFirstPendingExecutableTask(id);
    }

    @Override
    public List<ProcessInstance> findProcessInstanceByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return this.inMemoryKikwiEngineRepository.findProcessInstanceByProcessDefinitionId(processDefinitionId, tenantId);
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, String tenantId) {
        return this.inMemoryKikwiEngineRepository.findExternalTasksByProcessDefinitionId(processDefinitionId, tenantId);
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId) {
        return this.inMemoryKikwiEngineRepository.findExternalTasksByProcessDefinitionId(processDefinitionId);
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, List<String> tenantIds) {
        return this.inMemoryKikwiEngineRepository.findExternalTasksByProcessDefinitionId(processDefinitionId, tenantIds);
    }

    @Override
    public List<ExternalTask> findExternalTasksByAssignee(String assignee, String tenantId) {
        return this.inMemoryKikwiEngineRepository.findExternalTasksByAssignee(assignee, tenantId);
    }

    @Override
    public ExternalTaskQuery createExternalTaskQuery() {
        return inMemoryKikwiEngineRepository.createExternalTaskQuery();
    }

    @Override
    public ProcessInstanceQuery createProcessInstanceQuery() {
        return inMemoryKikwiEngineRepository.createProcessInstanceQuery();
    }

    @Override
    public Map<String, KKFMetrics> getMetricsByNodeForProcessDefinition(String processDefinitionId) {
        return this.inMemoryKikwiEngineRepository.getMetricsByNodeForProcessDefinition(processDefinitionId);
    }

    @Override
    public Optional<ProcessDefinition> findByKeyAndChecksum(String key, String checksum) {
        return inMemoryKikwiEngineRepository.findByKeyAndChecksum(key, checksum);
    }

    @Override
    public Optional<ProcessDefinition> findLatestVersionByKey(String key) {
        return inMemoryKikwiEngineRepository.findLatestVersionByKey(key);
    }

    @Override
    public List<ExecutableTask> findExecutableTasksByProcessInstanceId(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findExecutableTasksByProcessInstanceId(processInstanceId);
    }

    @Override
    public List<OutboxEventEntity> findEventHistoryByProcessInstanceId(String processInstanceId) {
        return this.inMemoryKikwiEngineRepository.findEventHistoryByProcessInstanceId(processInstanceId);
    }

    public void evaluateEvents(){
        this.assertableEventListener.runOnce();
    }

    public void reset() {
        inMemoryKikwiEngineRepository.reset();
    }

    public void assertThatProcessInstanceNotExistsInRuntimeContext(String processInstanceId){
        Optional<ProcessInstance> hotProcessInstanceOpt = inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
        assertFalse(hotProcessInstanceOpt.isPresent());
    }

    public void assertHasActiveExternalTaskOn(String processInstanceId, String taskDefinitionId) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        long matching = tasks.stream().filter(task -> task.taskDefinitionId().equals(taskDefinitionId)).count();
        assertEquals(1, matching, "Expected exactly one active external task with definition ID '" + taskDefinitionId + "', but found " + matching);
    }

    public void assertHasntActiveExternalTaskOn(String processInstanceId, String taskDefinitionId) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        assertTrue(tasks.stream().noneMatch(task -> task.taskDefinitionId().equals(taskDefinitionId)),
                "Expected to not find an active external task with definition ID '" + taskDefinitionId + "' but one was found.");
    }

    /**
     * Conta as tarefas-filhas (EVENT_CATCHER_CHILD) ainda pendentes (status CREATED) para o nó GROUP com o
     * {@code taskDefinitionId} informado. Filhas já correlacionadas (status CORRELATED) não contam como
     * pendentes — elas continuam na coleção até a tarefa-mãe concluir, mas não estão mais aguardando nada
     * (ver {@code assertHasCorrelatedEventCatcherChildren}).
     */
    public void assertHasPendingEventCatcherChildren(String processInstanceId, String taskDefinitionId, int expectedCount) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        long matching = tasks.stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId())
                        && t.coordinatorTaskId() != null
                        && t.status() == io.kikwiflow.model.execution.enumerated.ExternalTaskStatus.CREATED)
                .count();
        assertEquals(expectedCount, matching,
                "Expected " + expectedCount + " pending EVENT_CATCHER children for '" + taskDefinitionId + "', but found " + matching);
    }

    /**
     * Conta as tarefas-filhas já correlacionadas (status CORRELATED, ainda não limpas pela cascata) para o nó
     * GROUP com o {@code taskDefinitionId} informado — usado para confirmar que displayName/correlationKey
     * sobrevivem à correlação em vez de serem apagados imediatamente.
     */
    public void assertHasCorrelatedEventCatcherChildren(String processInstanceId, String taskDefinitionId, int expectedCount) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        long matching = tasks.stream()
                .filter(t -> taskDefinitionId.equals(t.taskDefinitionId())
                        && t.coordinatorTaskId() != null
                        && t.status() == io.kikwiflow.model.execution.enumerated.ExternalTaskStatus.CORRELATED)
                .count();
        assertEquals(expectedCount, matching,
                "Expected " + expectedCount + " correlated (not-yet-cleaned-up) EVENT_CATCHER children for '" + taskDefinitionId + "', but found " + matching);
    }

    /**
     * Confirma que nem a tarefa-mãe nem nenhuma tarefa-filha do EVENT_CATCHER GROUP com o {@code taskDefinitionId}
     * informado restam ativas (a mãe foi completada/interrompida e a cascata de limpeza apagou as filhas).
     */
    public void assertEventCatcherResolved(String processInstanceId, String taskDefinitionId) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        assertTrue(tasks.stream().noneMatch(t -> taskDefinitionId.equals(t.taskDefinitionId())),
                "Expected no remaining EVENT_CATCHER tasks (parent or children) for '" + taskDefinitionId + "', but some were found.");
    }

    public void assertThatProcessInstanceIsActive(String processInstanceId) {
        Optional<ProcessInstance> hotProcessInstanceOpt = inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
        assertTrue(hotProcessInstanceOpt.isPresent(), "Process instance should still be active in runtime context.");
    }

    public void assertIfHasProcessInstanceInHistory(ProcessInstance processInstance){
        Optional<ProcessInstanceFinished> coldProcessInstanceOpt = processInstanceSnapshotRepository.findById(processInstance.id());
        assertTrue(coldProcessInstanceOpt.isPresent());
        ProcessInstanceFinished savedProcessInstance = coldProcessInstanceOpt.get();
        assertEquals(processInstance.id(), savedProcessInstance.getId());
        assertEquals(processInstance.businessKey(), savedProcessInstance.getBusinessKey());
        assertEquals(processInstance.processDefinitionId(), savedProcessInstance.getProcessDefinitionId());
        assertEquals(processInstance.status(), savedProcessInstance.getStatus());
        assertEquals(processInstance.variables(), savedProcessInstance.getVariables());
    }

    public void assertThatProcessInstanceIsCompleted(String processInstanceId) {
        // Na implementação atual, uma instância completa é removida da coleção ativa.
        // Esta asserção verifica se a instância não foi encontrada, o que indica que foi concluída.
        assertFalse(findProcessInstanceById(processInstanceId).isPresent(),
                "A instância de processo " + processInstanceId + " deveria estar completa e não na coleção ativa.");
    }

    @Override
    public void ensureIndexes() {

    }
}
