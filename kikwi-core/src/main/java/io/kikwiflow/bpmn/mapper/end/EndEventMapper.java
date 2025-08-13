package io.kikwiflow.bpmn.mapper.end;

import io.kikwiflow.bpmn.mapper.SequenceFlowMapper;
import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.model.bpmn.elements.EndEventDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.FlowNodeDefinitionEntity;
import io.kikwiflow.persistence.api.data.bpmn.end.EndEventEntity;
import io.kikwiflow.persistence.api.data.bpmn.start.StartEventEntity;

import java.util.Objects;
import java.util.stream.Collectors;

public final class EndEventMapper {

    private EndEventMapper() {
        // Utility class
    }

    public static EndEventDefinitionSnapshot toSnapshot(final EndEvent endEvent) {
        if (Objects.isNull(endEvent)) {
            return null;
        }

        return EndEventDefinitionSnapshot.builder()
                .id(endEvent.getId())
                .name(endEvent.getName())
                .description(endEvent.getDescription())
                .commitAfter(endEvent.getCommitAfter())
                .commitBefore(endEvent.getCommitBefore())
                .build();
    }

    public static EndEventEntity toEntity(EndEventDefinitionSnapshot node) {
        return EndEventEntity.builder()
                .id(node.id())
                .name(node.name())
                .description(node.description())
                .commitAfter(node.commitAfter())
                .commitBefore(node.commitBefore())
                .build();
    }

    public static FlowNodeDefinitionSnapshot toSnapshot(EndEventEntity node) {
        if (Objects.isNull(node)) {
            return null;
        }

        return EndEventDefinitionSnapshot.builder()
                .id(node.getId())
                .name(node.getName())
                .description(node.getDescription())
                .commitAfter(node.getCommitAfter())
                .commitBefore(node.getCommitBefore())
                .build();
    }
}


