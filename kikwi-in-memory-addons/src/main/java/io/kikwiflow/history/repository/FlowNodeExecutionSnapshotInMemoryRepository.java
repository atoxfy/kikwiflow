package io.kikwiflow.history.repository;

import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.persistence.api.data.event.FlowNodeExecuted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FlowNodeExecutionSnapshotInMemoryRepository implements FlowNodeExecutionSnapshotRepository {

    private Map<String, List<FlowNodeExecuted>> coveredElements = new HashMap<String, List<FlowNodeExecuted>>();


    @Override
    public void save(FlowNodeExecuted flowNodeExecutionSnapshot) {
        String processInstanceId = flowNodeExecutionSnapshot.getProcessInstanceId();

        List<FlowNodeExecuted> processInstanceIdElements = coveredElements.get(processInstanceId);
        if(Objects.isNull(processInstanceIdElements)){
            processInstanceIdElements = new ArrayList<>();
        }

        processInstanceIdElements.add(flowNodeExecutionSnapshot);
        coveredElements.put(processInstanceId, processInstanceIdElements);
    }
}
