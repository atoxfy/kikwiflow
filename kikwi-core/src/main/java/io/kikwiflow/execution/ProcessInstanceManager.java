package io.kikwiflow.execution;

import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.ProcessExecutionRepository;

public class ProcessInstanceManager {
    private final ProcessExecutionRepository processExecutionRepository;

    public ProcessInstanceManager(ProcessExecutionRepository processExecutionRepository) {
        this.processExecutionRepository = processExecutionRepository;
    }


    public ProcessInstance create(ProcessInstance processInstance){
        //Todo check if has anoter processInstance with the same business key and processDefinition
        //if exists throw a duplicate key exception
        //else save the instance

        return processInstance;
    }
}
