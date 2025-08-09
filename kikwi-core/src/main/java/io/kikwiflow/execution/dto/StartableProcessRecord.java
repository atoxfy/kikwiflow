package io.kikwiflow.execution.dto;

import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.execution.ProcessInstance;

public record StartableProcessRecord(ProcessDefinitionSnapshot processDefinition, ProcessInstance processInstance) {
}
