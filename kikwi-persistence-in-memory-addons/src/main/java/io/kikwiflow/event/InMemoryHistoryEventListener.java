package io.kikwiflow.event;


import io.kikwiflow.event.model.ElementCoveredEvent;
import io.kikwiflow.event.model.ProcessInstanceFinishedEvent;
import io.kikwiflow.model.execution.ProcessInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InMemoryHistoryEventListener implements ExecutionEventListener {

    private Map<String, ProcessInstanceFinishedEvent> processInstanceHistoryCollection = new HashMap<>();

    public void saveProcessInstance(ProcessInstanceFinishedEvent processInstance) {
        processInstanceHistoryCollection.put(processInstance.id(), processInstance);
    }

    public Optional<ProcessInstanceFinishedEvent> getProcessInstanceById(String processInstanceId){
        return Optional.ofNullable(processInstanceHistoryCollection.get(processInstanceId));
    }


    private Map<String, List<ElementCoveredEvent>> coveredElements = new HashMap<String, List<ElementCoveredEvent>>();




    public void registerCoverage(ElementCoveredEvent elementCoveredEvent){
        String processInstanceId = elementCoveredEvent.processInstanceId();

        List<ElementCoveredEvent> processInstanceIdElements = coveredElements.get(processInstanceId);
        if(Objects.isNull(processInstanceIdElements)){
            processInstanceIdElements = new ArrayList<>();
        }

        processInstanceIdElements.add(elementCoveredEvent);

        coveredElements.put(processInstanceId, processInstanceIdElements);
    }


    @Override
    public void onEvents(List<ExecutionEvent> events) {
        if(Objects.isNull(events)) return;

        events.forEach(event -> {

            //Classificar, nos proximos em persistencia com BD fazer em insert em bulk
            if(event instanceof ElementCoveredEvent elementCoveredEvent){
                registerCoverage(elementCoveredEvent);
            } else if (event instanceof ProcessInstanceFinishedEvent processInstance) {
                saveProcessInstance(processInstance);
            }
            //Processar


        });
    }
}
