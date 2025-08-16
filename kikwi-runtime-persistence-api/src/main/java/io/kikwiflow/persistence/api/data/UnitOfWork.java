package io.kikwiflow.persistence.api.data;


import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.event.OutboxEventEntity;

import java.util.List;

public record UnitOfWork(
        ProcessInstance instanceToUpdate,
        ProcessInstance instanceToDelete,
        List<ExecutableTask> executableTasksToCreate,
        List<ExternalTask> externalTasksToCreate,
        List<String> tasksToDelete,
        List<OutboxEventEntity> events) {}