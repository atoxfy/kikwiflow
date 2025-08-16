package io.kikwiflow.history.repository;

import io.kikwiflow.model.event.ProcessInstanceFinished;

import java.util.Optional;

public interface ProcessInstanceSnapshotRepository {
    public void save(ProcessInstanceFinished processInstance);
    public Optional<ProcessInstanceFinished> findById(String processInstanceId);
}
