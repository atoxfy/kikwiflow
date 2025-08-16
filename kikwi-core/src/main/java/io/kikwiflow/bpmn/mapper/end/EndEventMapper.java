package io.kikwiflow.bpmn.mapper.end;

import io.kikwiflow.bpmn.model.end.EndEvent;
import io.kikwiflow.model.bpmn.elements.EndEventDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.persistence.api.data.bpmn.end.EndEventEntity;

import java.util.Objects;

public final class EndEventMapper {

    private EndEventMapper() {
        // Utility class
    }

    public static EndEventDefinition toSnapshot(final EndEvent endEvent) {
        if (Objects.isNull(endEvent)) {
            return null;
        }

        return EndEventDefinition.builder()
                .id(endEvent.getId())
                .name(endEvent.getName())
                .description(endEvent.getDescription())
                .commitAfter(endEvent.getCommitAfter())
                .commitBefore(endEvent.getCommitBefore())
                .build();
    }

    public static EndEventEntity toEntity(EndEventDefinition node) {
        return EndEventEntity.builder()
                .id(node.id())
                .name(node.name())
                .description(node.description())
                .commitAfter(node.commitAfter())
                .commitBefore(node.commitBefore())
                .build();
    }

    public static FlowNodeDefinition toSnapshot(EndEventEntity node) {
        if (Objects.isNull(node)) {
            return null;
        }

        return EndEventDefinition.builder()
                .id(node.getId())
                .name(node.getName())
                .description(node.getDescription())
                .commitAfter(node.getCommitAfter())
                .commitBefore(node.getCommitBefore())
                .build();
    }
}


