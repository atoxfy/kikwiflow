package io.kikwiflow.persistence.api.data.event;

import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.persistence.api.data.ProcessInstanceEntity;

import java.time.Instant;

public class ProcessInstanceFinished extends ProcessInstanceEntity implements CriticalEvent {

}
