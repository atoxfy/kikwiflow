package io.kikwiflow.bpmn.mapper.task;

import io.kikwiflow.bpmn.model.task.Service;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;

import java.util.Objects;
import java.util.stream.Collectors;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceEntity;

public final class ServiceTaskMapper {

    private ServiceTaskMapper() {
        // Utility class
    }

    public static ServiceTaskDefinition toSnapshot(final Service serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return ServiceTaskDefinition.builder()
                .id(serviceTask.getId())
                .name(serviceTask.getName())
                .description(serviceTask.getDescription())
                .delegateExpression(serviceTask.getDelegateExpression())
                .commitAfter(serviceTask.getCommitAfter())
                .commitBefore(serviceTask.getCommitBefore())
                .outgoing(serviceTask.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }

    public static ServiceEntity toEntity(ServiceTaskDefinition serviceTask) {

        return ServiceEntity.builder()
                .id(serviceTask.id())
                .name(serviceTask.name())
                .description(serviceTask.description())
                .delegateExpression(serviceTask.delegateExpression())
                .commitAfter(serviceTask.commitAfter())
                .commitBefore(serviceTask.commitBefore())
                .outgoing(serviceTask.outgoing().stream()
                        .map(SequenceFlowMapper::toEntity)
                        .collect(Collectors.toList()))
                .build();
    }

    public static FlowNodeDefinition toSnapshot(ServiceEntity serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return ServiceTaskDefinition.builder()
                .id(serviceTask.getId())
                .name(serviceTask.getName())
                .description(serviceTask.getDescription())
                .delegateExpression(serviceTask.getDelegateExpression())
                .commitAfter(serviceTask.getCommitAfter())
                .commitBefore(serviceTask.getCommitBefore())
                .outgoing(serviceTask.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }
}

