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
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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

    @Override
    public ProcessInstance saveProcessInstance(ProcessInstance instance) {
        return inMemoryKikwiEngineRepository.saveProcessInstance(instance);
    }

    @Override
    public List<ProcessDefinition> findAProcessDefinitionsByParams(String key) {
        return List.of();
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
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId) {
        return this.inMemoryKikwiEngineRepository.findAndLockDueTasks(now, limit, workerId);
    }

    @Override
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables) {
        return null;
    }

    @Override
    public void claim(String externalTaskId, String assignee) {
        this.inMemoryKikwiEngineRepository.claim(externalTaskId, assignee);
    }

    @Override
    public void unclaim(String externalTaskId) {
        this.inMemoryKikwiEngineRepository.unclaim(externalTaskId);
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {

    }

    @Override
    public Optional<ExternalTask> findExternalTaskById(String externalTaskId) {
        return inMemoryKikwiEngineRepository.findExternalTaskById(externalTaskId);
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        return Optional.empty();
    }

    @Override
    public Optional<ExecutableTask> findExecutableTaskById(String executableTaskId) {
        return Optional.empty();
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
        return null;
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
        assertTrue(tasks.stream().anyMatch(task -> task.taskDefinitionId().equals(taskDefinitionId)),
            "Expected to find an active external task with definition ID '" + taskDefinitionId + "' but none was found.");
        assertEquals(1, tasks.size(), "Expected exactly one active external task, but found " + tasks.size());
    }

    public void assertHasntActiveExternalTaskOn(String processInstanceId, String taskDefinitionId) {
        List<ExternalTask> tasks = inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
        assertTrue(tasks.stream().noneMatch(task -> task.taskDefinitionId().equals(taskDefinitionId)),
                "Expected to not find an active external task with definition ID '" + taskDefinitionId + "' but one was found.");
        assertEquals(0, tasks.size(), "Expected exactly no one active external task, but found " + tasks.size());
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
