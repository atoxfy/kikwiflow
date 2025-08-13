package io.kikwiflow.bpmn.mapper;

import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.model.bpmn.elements.SequenceFlowDefinitionSnapshot;

import java.util.Objects;

import io.kikwiflow.bpmn.model.SequenceFlow;
import io.kikwiflow.model.bpmn.elements.SequenceFlowDefinitionSnapshot;
import io.kikwiflow.persistence.api.data.bpmn.SequenceFlowEntity;
import io.kikwiflow.persistence.api.data.bpmn.task.ServiceTaskEntity;

import java.util.Objects;

public final class SequenceFlowMapper {

    private SequenceFlowMapper() {
        // Utility class
    }

    public static SequenceFlowDefinitionSnapshot toSnapshot(final SequenceFlowEntity sequenceFlow) {
        if (Objects.isNull(sequenceFlow)) {
            return null;
        }

        return new SequenceFlowDefinitionSnapshot(sequenceFlow.getId(), sequenceFlow.getCondition(), sequenceFlow.getTargetNodeId());
    }

    public static SequenceFlowDefinitionSnapshot toSnapshot(final SequenceFlow sequenceFlow) {
        if (Objects.isNull(sequenceFlow)) {
            return null;
        }

        return new SequenceFlowDefinitionSnapshot(sequenceFlow.getId(), sequenceFlow.getCondition(), sequenceFlow.getTargetNodeId());
    }

    public static SequenceFlowEntity toEntity(SequenceFlowDefinitionSnapshot sequenceFlowDefinitionSnapshot) {
        SequenceFlowEntity sequenceFlow = new SequenceFlowEntity();
        sequenceFlow.setCondition(sequenceFlowDefinitionSnapshot.condition());
        sequenceFlow.setId(sequenceFlowDefinitionSnapshot.id());
        sequenceFlow.setTargetNodeId(sequenceFlowDefinitionSnapshot.targetNodeId());
        return sequenceFlow;
    }
}
