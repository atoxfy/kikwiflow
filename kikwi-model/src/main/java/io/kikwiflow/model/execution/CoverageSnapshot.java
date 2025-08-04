package io.kikwiflow.model.execution;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.enumerated.CoveredElementStatus;

import java.time.Instant;

public record CoverageSnapshot (FlowNode flowNode, ProcessDefinition processDefinition, ProcessInstance processInstance, Instant startedAt, Instant finishedAt, CoveredElementStatus status) {
}
