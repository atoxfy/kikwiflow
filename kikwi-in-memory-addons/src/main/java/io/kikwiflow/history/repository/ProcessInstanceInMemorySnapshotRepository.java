package io.kikwiflow.history.repository;

import io.kikwiflow.model.execution.ProcessInstanceSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProcessInstanceInMemorySnapshotRepository implements ProcessInstanceSnapshotRepository {

    private Map<String, ProcessInstanceSnapshot> processInstanceHistoryCollection = new HashMap<>();

    @Override
    public void save(ProcessInstanceSnapshot processInstance) {
        processInstanceHistoryCollection.put(processInstance.id(), processInstance);

    }

    @Override
    public Optional<ProcessInstanceSnapshot> findById(String processInstanceId) {
        return Optional.ofNullable(processInstanceHistoryCollection.get(processInstanceId));
    }
}
