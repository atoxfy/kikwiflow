package io.kikwiflow.bpmn.mapper;

import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.model.bpmn.elements.SequenceFlowDefinition;

import java.util.Objects;

import io.kikwiflow.persistence.api.data.bpmn.SequenceFlowEntity;

public final class SequenceFlowMapper {

    private SequenceFlowMapper() {
        // Utility class
    }

    public static SequenceFlowDefinition toSnapshot(final SequenceFlowEntity sequenceFlow) {
        if (Objects.isNull(sequenceFlow)) {
            return null;
        }

        return new SequenceFlowDefinition(sequenceFlow.getId(), sequenceFlow.getCondition(), sequenceFlow.getTargetNodeId());
    }

    public static SequenceFlowDefinition toSnapshot(final SequenceFlow sequenceFlow) {
        if (Objects.isNull(sequenceFlow)) {
            return null;
        }

        return new SequenceFlowDefinition(sequenceFlow.getId(), sequenceFlow.getCondition(), sequenceFlow.getTargetNodeId());
    }

    public static SequenceFlowEntity toEntity(SequenceFlowDefinition sequenceFlowDefinition) {
        SequenceFlowEntity sequenceFlow = new SequenceFlowEntity();
        sequenceFlow.setCondition(sequenceFlowDefinition.condition());
        sequenceFlow.setId(sequenceFlowDefinition.id());
        sequenceFlow.setTargetNodeId(sequenceFlowDefinition.targetNodeId());
        return sequenceFlow;
    }
}
