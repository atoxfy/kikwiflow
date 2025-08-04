package io.kikwiflow.execution;

import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.KikwiflowEngineRepository;

public class ProcessInstanceManager {
    private final KikwiflowEngineRepository kikwiflowEngineRepository;

    public ProcessInstanceManager(KikwiflowEngineRepository kikwiflowEngineRepository) {
        this.kikwiflowEngineRepository = kikwiflowEngineRepository;
    }


    public ProcessInstance create(ProcessInstance processInstance){
        //Todo check if has anoter processInstance with the same business key and processDefinition
        //if exists throw a duplicate key exception
        //else save the instance

        return processInstance;
    }
}
