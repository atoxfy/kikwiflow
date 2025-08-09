package io.kikwiflow.execution;

import io.kikwiflow.event.EventPublisher;
import io.kikwiflow.event.model.ProcessInstanceFinishedEvent;
import io.kikwiflow.exception.ProcessInstanceNotFoundException;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.KikwiEngineRepository;

import java.time.Instant;
import java.util.Map;

import static io.kikwiflow.execution.mapper.ProcessInstanceMapper.toFinishedEvent;
import static io.kikwiflow.execution.mapper.ProcessInstanceMapper.toSnapshot;

public class ProcessInstanceManager {

    private final KikwiEngineRepository kikwiEngineRepository;
    private final EventPublisher eventPublisher;

    public ProcessInstanceManager(KikwiEngineRepository kikwiEngineRepository, EventPublisher eventPublisher) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.eventPublisher = eventPublisher;
    }


    public ProcessInstance start(String businessKey, String processDefinitionId, Map<String, Object> variables){
        final ProcessInstance processInstance = ProcessInstance.builder()
                .businessKey(businessKey)
                .processDefinitionId(processDefinitionId)
                .variables(variables)
                .build();

        return kikwiEngineRepository.saveProcessInstance(processInstance);
    }

    /**
     * Completes a given process instance. This method assumes the passed instance
     * contains the final state of the execution.
     *
     * @param processInstance The process instance with its final state.
     * @return An immutable snapshot of the completed instance.
     */
    public ProcessInstanceSnapshot complete(ProcessInstance processInstance) {
        processInstance.setStatus(ProcessInstanceStatus.COMPLETED);
        processInstance.setEndedAt(Instant.now());

        final ProcessInstanceFinishedEvent event = toFinishedEvent(processInstance);
        eventPublisher.publishEvent(event);
        kikwiEngineRepository.deleteProcessInstanceById(processInstance.getId());
        return toSnapshot(processInstance);
    }

    public void update(ProcessInstance processInstance) {
        kikwiEngineRepository.updateProcessInstance(processInstance);
    }
}
