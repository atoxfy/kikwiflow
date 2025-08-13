package io.kikwiflow.bpmn.mapper.start;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.start.StartEvent;
import io.kikwiflow.model.bpmn.elements.EndEventDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.StartEventDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.start.StartEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceTaskEntity;

import java.util.stream.Collectors;

public class StartEventMapper {

    private StartEventMapper() {
        // Utility class
    }

    public static StartEventDefinitionSnapshot toSnapshot(StartEvent node) {
        return StartEventDefinitionSnapshot.builder()
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

    public static StartEventEntity toEntity(StartEventDefinitionSnapshot node) {
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

    public static StartEventDefinitionSnapshot toSnapshot(StartEventEntity node) {
        return StartEventDefinitionSnapshot.builder()
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
