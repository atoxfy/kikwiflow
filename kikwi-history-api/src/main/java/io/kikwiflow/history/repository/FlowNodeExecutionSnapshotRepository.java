package io.kikwiflow.history.repository;

import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.persistence.api.data.event.FlowNodeExecuted;

public interface FlowNodeExecutionSnapshotRepository {
    public void save(FlowNodeExecuted flowNodeExecutionSnapshot);
}
