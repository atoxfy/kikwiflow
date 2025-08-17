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
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;

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
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        inMemoryKikwiEngineRepository.updateVariables(processInstanceId, variables);
    }

    @Override
    public ExternalTask createExternalTask(ExternalTask task) {
        return inMemoryKikwiEngineRepository.createExternalTask(task);
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findExternalTasksByProcessInstanceId(processInstanceId);
    }

    @Override
    public ExecutableTask createExecutableTask(ExecutableTask task) {
        return inMemoryKikwiEngineRepository.createExecutableTask(task);
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
    public ProcessInstance updateProcessInstance(ProcessInstance processInstance) {
        return null;
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        this.inMemoryKikwiEngineRepository.deleteProcessInstanceById(processInstanceId);
    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        this.inMemoryKikwiEngineRepository.commitWork(unitOfWork);
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
}
