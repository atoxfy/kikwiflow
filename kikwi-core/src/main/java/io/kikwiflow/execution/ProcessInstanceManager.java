package io.kikwiflow.execution;

import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.execution.dto.UnitOfWorkResult;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.persistence.api.data.ProcessInstanceEntity;
import io.kikwiflow.persistence.api.data.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.Map;

import static io.kikwiflow.execution.mapper.ProcessInstanceMapper.toFinishedEvent;
import static io.kikwiflow.execution.mapper.ProcessInstanceMapper.takeSnapshot;

public class ProcessInstanceManager {

    private final KikwiEngineRepository kikwiEngineRepository;
    private final AsynchronousEventPublisher asynchronousEventPublisher;

    public ProcessInstanceManager(KikwiEngineRepository kikwiEngineRepository, AsynchronousEventPublisher asynchronousEventPublisher) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.asynchronousEventPublisher = asynchronousEventPublisher;
    }


    public ProcessInstance start(String businessKey, String processDefinitionId, Map<String, Object> variables){
        final ProcessInstanceEntity processInstance = ProcessInstanceEntity.builder()
                .businessKey(businessKey)
                .processDefinitionId(processDefinitionId)
                .variables(variables)
                .build();

        return ProcessInstanceMapper.toProcessInstance(kikwiEngineRepository.saveProcessInstance(processInstance));
    }



    public void update(ProcessInstance processInstance) {
        kikwiEngineRepository.updateProcessInstance(ProcessInstanceMapper.mapToEntity(processInstance));
    }
}
