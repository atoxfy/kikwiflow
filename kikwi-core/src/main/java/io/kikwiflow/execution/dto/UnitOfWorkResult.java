package io.kikwiflow.execution.dto;

import io.kikwiflow.persistence.api.data.UnitOfWork;

public record UnitOfWorkResult(UnitOfWork unitOfWork, Continuation continuation) {
}
