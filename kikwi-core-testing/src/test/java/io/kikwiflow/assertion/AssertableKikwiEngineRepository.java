package io.kikwiflow.assertion;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ExecutableTaskEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.api.model.OutboxEvent;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.mockito.Mockito.spy;

public class AssertableKikwiEngineRepository implements KikwiEngineRepository {

    private final InMemoryKikwiEngineRepository repository;
    private final KikwiEngineRepository spy;

    public AssertableKikwiEngineRepository(Queue<OutboxEvent> outboxEventQueue){
        this.repository = new InMemoryKikwiEngineRepository(outboxEventQueue);
        this.spy = spy(repository);
    }

    @Override
    public ProcessInstance saveProcessInstance(ProcessInstance instance) {
        return spy.saveProcessInstance(instance);
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        return spy.findProcessInstanceById(processInstanceId);
    }

    @Override
    public void updateVariables(String processInstanceId, Map<String, Object> variables) {
        spy.updateVariables(processInstanceId, variables);
    }

    @Override
    public ExecutableTaskEntity createExecutableTask(ExecutableTaskEntity task) {
        return spy.createExecutableTask(task);
    }


    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy) {
        return spy.saveProcessDefinition(processDefinitionDeploy);
    }

    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey) {
        return spy.findProcessDefinitionByKey(processDefinitionKey);
    }

    @Override
    public ProcessInstance updateProcessInstance(ProcessInstance processInstance) {
        return null;
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {

    }

}
