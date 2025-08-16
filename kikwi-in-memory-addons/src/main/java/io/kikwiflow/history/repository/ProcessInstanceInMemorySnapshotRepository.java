package io.kikwiflow.history.repository;

import io.kikwiflow.model.event.ProcessInstanceFinished;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProcessInstanceInMemorySnapshotRepository implements ProcessInstanceSnapshotRepository {

    private Map<String, ProcessInstanceFinished> processInstanceHistoryCollection = new HashMap<>();

    @Override
    public void save(ProcessInstanceFinished processInstance) {
        processInstanceHistoryCollection.put(processInstance.getId(), processInstance);

    }

    @Override
    public Optional<ProcessInstanceFinished> findById(String processInstanceId) {
        return Optional.ofNullable(processInstanceHistoryCollection.get(processInstanceId));
    }
}
