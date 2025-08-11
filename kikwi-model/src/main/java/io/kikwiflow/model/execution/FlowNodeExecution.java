package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;

import java.time.Instant;

public record FlowNodeExecution(
        FlowNodeDefinitionSnapshot flowNodeDefinition,
        ProcessDefinitionSnapshot processDefinitionSnapshot,
        ProcessInstanceSnapshot processInstanceSnapshot,
        Instant startedAt,
        Instant finishedAt,
        NodeExecutionStatus nodeExecutionStatus) {
}
