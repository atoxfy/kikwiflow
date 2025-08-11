package io.kikwiflow.history.repository;

import io.kikwiflow.model.execution.FlowNodeExecution;

public interface FlowNodeExecutionRepository {
    public void save(FlowNodeExecution flowNodeExecution);
}
