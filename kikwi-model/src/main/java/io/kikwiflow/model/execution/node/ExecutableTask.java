package io.kikwiflow.model.execution.node;

import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;

import java.time.Instant;

public record ExecutableTask (String id,
                               String taskDefinitionId,
                               String name,
                               String description,
                               String processDefinitionId,
                               Instant createdAt,
                               Long executions,
                               Long retries,
                               String processInstanceId,
                               String error,
                               ExecutableTaskStatus status,
                               String executorId,
                               Instant acquiredAt){


}
