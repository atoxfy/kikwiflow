package io.kikwiflow.history.repository;

import io.kikwiflow.model.event.FlowNodeExecuted;

public interface FlowNodeExecutionSnapshotRepository {
    public void save(FlowNodeExecuted flowNodeExecutionSnapshot);
}
