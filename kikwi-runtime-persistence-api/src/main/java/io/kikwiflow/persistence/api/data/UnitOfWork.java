package io.kikwiflow.persistence.api.data;


import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.persistence.api.data.event.CriticalEvent;
import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;

import java.util.List;

public record UnitOfWork(
        ProcessInstanceEntity instanceToUpdate,
        ProcessInstanceEntity instanceToDelete,
        List<ExecutableTaskEntity> tasksToCreate,
        List<String> tasksToDelete,
        List<OutboxEventEntity> events) {}