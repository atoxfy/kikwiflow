package io.kikwiflow.execution;

import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.util.Map;

import static io.kikwiflow.execution.mapper.ProcessInstanceMapper.takeSnapshot;

public class ProcessInstanceManager {

    private final KikwiEngineRepository kikwiEngineRepository;
    private final AsynchronousEventPublisher asynchronousEventPublisher;

    public ProcessInstanceManager(KikwiEngineRepository kikwiEngineRepository, AsynchronousEventPublisher asynchronousEventPublisher) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.asynchronousEventPublisher = asynchronousEventPublisher;
    }


    public ProcessInstanceExecution start(String businessKey, String processDefinitionId, Map<String, Object> variables){
        final ProcessInstance processInstance = ProcessInstance.builder()
                .businessKey(businessKey)
                .processDefinitionId(processDefinitionId)
                .variables(variables)
                .build();

        return ProcessInstanceMapper.toProcessInstance(kikwiEngineRepository.saveProcessInstance(processInstance));
    }



    public void update(ProcessInstanceExecution processInstance) {
        kikwiEngineRepository.updateProcessInstance(ProcessInstanceMapper.mapToEntity(processInstance));
    }
}
