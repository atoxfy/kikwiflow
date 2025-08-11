package io.kikwiflow.history.repository;

import io.kikwiflow.model.execution.ProcessInstanceSnapshot;

import java.util.Optional;

public interface ProcessInstanceSnapshotRepository {
    public void save(ProcessInstanceSnapshot processInstance);
    public Optional<ProcessInstanceSnapshot> findById(String processInstanceId);
}
