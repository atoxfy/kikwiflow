package io.kikwiflow.execution.dto;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.execution.ProcessInstanceExecution;

public record StartableProcessRecord(ProcessDefinition processDefinition, ProcessInstanceExecution processInstance) {
}
