package io.kikwiflow.execution.dto;

import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.execution.ProcessInstance;

public record StartableProcessRecord(ProcessDefinitionSnapshot processDefinition, ProcessInstance processInstance) {
}
