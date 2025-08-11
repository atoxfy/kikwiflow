package io.kikwiflow.history.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FlowNodeExecutionInMemoryRepository {
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
}
