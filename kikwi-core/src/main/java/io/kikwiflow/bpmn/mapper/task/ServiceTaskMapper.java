package io.kikwiflow.bpmn.mapper.task;

import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinitionSnapshot;

import java.util.Objects;
import java.util.stream.Collectors;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.task.ServiceTask;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceTaskEntity;

import java.util.Objects;
import java.util.stream.Collectors;

public final class ServiceTaskMapper {

    private ServiceTaskMapper() {
        // Utility class
    }

    public static ServiceTaskDefinitionSnapshot toSnapshot(final ServiceTask serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return ServiceTaskDefinitionSnapshot.builder()
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

    public static ServiceTaskEntity toEntity(ServiceTaskDefinitionSnapshot serviceTask) {
        return ServiceTaskEntity.builder()
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

    public static FlowNodeDefinitionSnapshot toSnapshot(ServiceTaskEntity serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return ServiceTaskDefinitionSnapshot.builder()
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

