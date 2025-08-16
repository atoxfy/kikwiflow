package io.kikwiflow.bpmn.mapper.task;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.task.HumanTask;
import io.kikwiflow.model.bpmn.elements.HumanTaskDefinition;
import io.kikwiflow.persistence.api.data.bpmn.task.HumanTaskEntity;

import java.util.Objects;
import java.util.stream.Collectors;

public class HumanTaskMapper {

    private HumanTaskMapper() {
        // Utility class
    }

    public static HumanTaskDefinition toSnapshot(final HumanTask serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return HumanTaskDefinition.builder()
                .id(serviceTask.getId())
                .name(serviceTask.getName())
                .description(serviceTask.getDescription())
                .commitAfter(serviceTask.getCommitAfter())
                .commitBefore(serviceTask.getCommitBefore())
                .outgoing(serviceTask.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }

    public static HumanTaskEntity toEntity(HumanTaskDefinition serviceTask) {

        return HumanTaskEntity.builder()
                .id(serviceTask.id())
                .name(serviceTask.name())
                .description(serviceTask.description())
                .commitAfter(serviceTask.commitAfter())
                .commitBefore(serviceTask.commitBefore())
                .outgoing(serviceTask.outgoing().stream()
                        .map(SequenceFlowMapper::toEntity)
                        .collect(Collectors.toList()))
                .build();
    }

    public static HumanTaskDefinition toSnapshot(HumanTaskEntity serviceTask) {
        if (Objects.isNull(serviceTask)) {
            return null;
        }
        return HumanTaskDefinition.builder()
                .id(serviceTask.getId())
                .name(serviceTask.getName())
                .description(serviceTask.getDescription())
                .commitAfter(serviceTask.getCommitAfter())
                .commitBefore(serviceTask.getCommitBefore())
                .outgoing(serviceTask.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }
}
