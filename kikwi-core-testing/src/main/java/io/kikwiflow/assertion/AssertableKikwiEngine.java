package io.kikwiflow.assertion;

import io.kikwiflow.history.repository.FlowNodeExecutionSnapshotInMemoryRepository;
import io.kikwiflow.history.repository.ProcessInstanceInMemorySnapshotRepository;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.ExecutableTaskEntity;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.persistence.api.data.ProcessDefinitionEntity;
import io.kikwiflow.persistence.api.data.ProcessInstanceEntity;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;
import io.kikwiflow.persistence.api.data.event.ProcessInstanceFinished;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;

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
    public ProcessInstanceEntity saveProcessInstance(ProcessInstanceEntity instance) {
        return inMemoryKikwiEngineRepository.saveProcessInstance(instance);
    }

    @Override
    public Optional<ProcessInstanceEntity> findProcessInstanceById(String processInstanceId) {
        return inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        inMemoryKikwiEngineRepository.updateVariables(processInstanceId, variables);
    }

    @Override
    public ExecutableTaskEntity createExecutableTask(ExecutableTaskEntity task) {
        return inMemoryKikwiEngineRepository.createExecutableTask(task);
    }


    @Override
    public ProcessDefinitionEntity saveProcessDefinition(ProcessDefinitionEntity processDefinitionDeploy) {
        return inMemoryKikwiEngineRepository.saveProcessDefinition(processDefinitionDeploy);
    }

    @Override
    public Optional<ProcessDefinitionEntity> findProcessDefinitionByKey(String processDefinitionKey) {
        return inMemoryKikwiEngineRepository.findProcessDefinitionByKey(processDefinitionKey);
    }

    @Override
    public ProcessInstanceEntity updateProcessInstance(ProcessInstanceEntity processInstance) {
        return null;
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {

    }

    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        this.inMemoryKikwiEngineRepository.commitWork(unitOfWork);
    }

    public void reset() {
        inMemoryKikwiEngineRepository.reset();
    }

    public void assertThatProcessInstanceNotExistsInRuntimeContext(String processInstanceId){
        Optional<ProcessInstanceEntity> hotProcessInstanceOpt = inMemoryKikwiEngineRepository.findProcessInstanceById(processInstanceId);
        assertFalse(hotProcessInstanceOpt.isPresent());
    }

    public void assertIfHasProcessInstanceInHistory(ProcessInstanceSnapshot processInstance){
        Optional<ProcessInstanceFinished> coldProcessInstanceOpt = processInstanceSnapshotRepository.findById(processInstance.id());
        assertTrue(coldProcessInstanceOpt.isPresent());
        ProcessInstanceEntity savedProcessInstance = coldProcessInstanceOpt.get();
        assertEquals(processInstance.id(), savedProcessInstance.getId());
        assertEquals(processInstance.businessKey(), savedProcessInstance.getBusinessKey());
        assertEquals(processInstance.processDefinitionId(), savedProcessInstance.getProcessDefinitionId());
        assertEquals(processInstance.status(), savedProcessInstance.getStatus());
        assertEquals(processInstance.variables(), savedProcessInstance.getVariables());
    }
}
