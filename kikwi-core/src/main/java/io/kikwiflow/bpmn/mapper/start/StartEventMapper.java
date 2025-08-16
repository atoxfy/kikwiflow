package io.kikwiflow.bpmn.mapper.start;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.model.bpmn.elements.StartEventDefinition;
import io.kikwiflow.persistence.api.data.bpmn.start.StartEventEntity;

import java.util.stream.Collectors;

public class StartEventMapper {

    private StartEventMapper() {
        // Utility class
    }

    public static StartEventDefinition toSnapshot(StartEvent node) {
        return StartEventDefinition.builder()
                .id(node.getId())
                .name(node.getName())
                .description(node.getDescription())
                .commitAfter(node.getCommitAfter())
                .commitBefore(node.getCommitBefore())
                .outgoing(node.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }

    public static StartEventEntity toEntity(StartEventDefinition node) {
        return StartEventEntity.builder()
                .id(node.id())
                .name(node.name())
                .description(node.description())
                .commitAfter(node.commitAfter())
                .commitBefore(node.commitBefore())
                .outgoing(node.outgoing().stream()
                        .map(SequenceFlowMapper::toEntity)
                        .collect(Collectors.toList()))
                .build();
    }

    public static StartEventDefinition toSnapshot(StartEventEntity node) {
        return StartEventDefinition.builder()
                .id(node.getId())
                .name(node.getName())
                .description(node.getDescription())
                .commitAfter(node.getCommitAfter())
                .commitBefore(node.getCommitBefore())
                .outgoing(node.getOutgoing().stream()
                        .map(SequenceFlowMapper::toSnapshot)
                        .collect(Collectors.toList()))
                .build();
    }
}
