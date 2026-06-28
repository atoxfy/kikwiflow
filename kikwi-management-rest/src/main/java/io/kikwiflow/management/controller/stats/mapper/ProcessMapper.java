package io.kikwiflow.management.controller.stats.mapper;

import io.kikwiflow.management.dtos.elements.KKFBoundaryEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFEndEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFExclusiveGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFExecutableTaskDefinition;
import io.kikwiflow.management.dtos.elements.KKFExternalTaskDefinition;
import io.kikwiflow.management.dtos.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.dtos.elements.KKFInterruptiveTimerEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFJoinGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFParallelGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFSequenceFlowDefinition;
import io.kikwiflow.management.dtos.elements.KKFStartEventDefinition;
import io.kikwiflow.management.dtos.layout.KKFLayoutCoordinates;
import io.kikwiflow.model.definition.process.elements.BoundaryEventDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.JoinGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import io.kikwiflow.model.stats.KKFMetrics;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessMapper {



    // Helper para converter Listas de Fluxo
    private static List<SequenceFlowDefinition> mapOutgoing(List<KKFSequenceFlowDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(dto -> new SequenceFlowDefinition(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.expectedAnswer(),
                dto.targetNodeId(),
                dto.isDefault(),
                dto.handlesNull(),
                mapPositionHandlers(dto.positionHandlers())
        )).collect(Collectors.toList());
    }


    private static List<KKFSequenceFlowDefinition> mapOutgoingK(List<SequenceFlowDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(dto -> new KKFSequenceFlowDefinition(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.expectedAnswer(),
                dto.targetNodeId(),
                dto.isDefault(),
                dto.handlesNull(),
                mapPositionHandlersk(dto.positionHandlers())
        )).collect(Collectors.toList());
    }

    private static List<BoundaryEventDefinition> mapBoundaryEvents(List<KKFBoundaryEventDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(kkfBoundaryEventDefinition ->  {
                    if(kkfBoundaryEventDefinition instanceof KKFInterruptiveTimerEventDefinition kkfInterruptiveTimerEventDefinition){
                        return new InterruptiveTimerEventDefinition(
                                kkfInterruptiveTimerEventDefinition.id(),
                                kkfInterruptiveTimerEventDefinition.name(),
                                kkfInterruptiveTimerEventDefinition.type(),
                                kkfInterruptiveTimerEventDefinition.description(),
                                kkfInterruptiveTimerEventDefinition.executor(),
                                kkfInterruptiveTimerEventDefinition.commitAfter(),
                                kkfInterruptiveTimerEventDefinition.commitBefore(),
                                mapOutgoing(kkfInterruptiveTimerEventDefinition.outgoing()),
                                kkfBoundaryEventDefinition.attachedToRef(),
                                kkfInterruptiveTimerEventDefinition.duration(),
                                kkfInterruptiveTimerEventDefinition.extensionProperties(),
                                mapLayout(kkfInterruptiveTimerEventDefinition.layout()));
                    }

                    return null;

                })
                .collect(Collectors.toList());
    }



    private static List<KKFBoundaryEventDefinition> mapBoundaryEventsK(List<BoundaryEventDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream()
                .map(kkfBoundaryEventDefinition ->  {
                    if(kkfBoundaryEventDefinition instanceof InterruptiveTimerEventDefinition kkfInterruptiveTimerEventDefinition){

                        return new KKFInterruptiveTimerEventDefinition(
                                kkfInterruptiveTimerEventDefinition.id(),
                                kkfInterruptiveTimerEventDefinition.name(),
                                kkfInterruptiveTimerEventDefinition.type(),
                                kkfInterruptiveTimerEventDefinition.description(),
                                kkfInterruptiveTimerEventDefinition.executor(),
                                kkfInterruptiveTimerEventDefinition.commitAfter(),
                                kkfInterruptiveTimerEventDefinition.commitBefore(),
                                mapOutgoingK(kkfInterruptiveTimerEventDefinition.outgoing()),
                                kkfBoundaryEventDefinition.attachedToRef(),
                                kkfInterruptiveTimerEventDefinition.duration(),
                                kkfInterruptiveTimerEventDefinition.extensionProperties(),
                                mapLayout(kkfInterruptiveTimerEventDefinition.layout()));
                    }

                    return null;

                })
                .collect(Collectors.toList());
    }


    // Helper para converter Layout (DTO -> Domain)
    private static LayoutCoordinates mapLayout(KKFLayoutCoordinates dtoCoords) {
        if (dtoCoords == null) return new LayoutCoordinates(0.0, 0.0);
        Double x = dtoCoords.x() != null ? dtoCoords.x().doubleValue() : 0.0;
        Double y = dtoCoords.y() != null ? dtoCoords.y().doubleValue() : 0.0;

        return new LayoutCoordinates(x, y);
    }


    private static KKFLayoutCoordinates mapLayout(LayoutCoordinates dtoCoords) {
        if (dtoCoords == null) return new KKFLayoutCoordinates(0.0, 0.0);
        Double x = dtoCoords.x() != null ? dtoCoords.x().doubleValue() : 0.0;
        Double y = dtoCoords.y() != null ? dtoCoords.y().doubleValue() : 0.0;

        return new KKFLayoutCoordinates(x, y);
    }


    // Helper para converter handlers da aresta
    private static List<LayoutCoordinates> mapPositionHandlers(List<KKFLayoutCoordinates> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(ProcessMapper::mapLayout).collect(Collectors.toList());
    }

    private static List<KKFLayoutCoordinates> mapPositionHandlersk(List<LayoutCoordinates> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(ProcessMapper::mapLayout).collect(Collectors.toList());
    }





    public static KKFFlowNodeDefinition mapNode(FlowNodeDefinition dto, KKFMetrics metrics) {
        if (dto == null) return null;

        if(dto instanceof  StartEventDefinition s) {
            return new KKFStartEventDefinition(
                    s.id(),
                    s.name(),
                    s.description(),
                    s.type(),
                    s.commitAfter(),
                    s.commitBefore(),
                    mapOutgoingK(s.outgoing()),
                    s.extensionProperties(),
                    mapLayout(s.layout())
            );
        } else if (dto instanceof  EndEventDefinition e ) {
            return new KKFEndEventDefinition(
                    e.id(),
                    e.name(),
                    e.type(),
                    e.description(),
                    e.commitAfter(),
                    e.commitBefore(),
                    null,
                    e.extensionProperties(),
                    mapLayout(e.layout())
            );
        } else if (dto instanceof  ExclusiveGatewayDefinition g) {
            return new KKFExclusiveGatewayDefinition(
                    g.id(),
                    g.name(),
                    g.type(),
                    g.description(),
                    g.commitAfter(),
                    g.commitBefore(),
                    g.defaultFlow(),
                    mapOutgoingK(g.outgoing()),
                    g.extensionProperties(),
                    ProcessMapper.mapLayout(g.layout()),
                    g.providerType(),
                    g.providerBean(),
                    g.providerVariable()
            );

        }else if (dto instanceof  ParallelGatewayDefinition g) {
                return new KKFParallelGatewayDefinition(
                        g.id(),
                        g.name(),
                        g.type(),
                        g.description(),
                        g.commitAfter(),
                        g.commitBefore(),
                        g.targetJoinId(),
                        mapOutgoingK(g.outgoing()),
                        g.extensionProperties(),
                        ProcessMapper.mapLayout(g.layout())
                );

        } else if (dto instanceof  JoinGatewayDefinition g) {
            return new KKFJoinGatewayDefinition(
                    g.id(),
                    g.name(),
                    g.type(),
                    g.description(),
                    g.commitAfter(),
                    g.commitBefore(),
                    mapOutgoingK(g.outgoing()),
                    g.extensionProperties(),
                    g.sourceSplitId(),
                    ProcessMapper.mapLayout(g.layout())
            );

        }else if (dto instanceof ExternalTaskDefinition t) {
            return new KKFExternalTaskDefinition(
                    t.id(),
                    t.name(),
                    t.type(),
                    t.description(),
                    t.commitAfter(),
                    t.commitBefore(),
                    metrics,
                    mapOutgoingK(t.outgoing()),
                    mapBoundaryEventsK(t.boundaryEvents()),
                    t.extensionProperties(),
                    mapLayout(t.layout())
            );
        }else if (dto instanceof ExecutableTaskDefinition exect) {

            return new KKFExecutableTaskDefinition(
                    exect.id(),
                    exect.name(),
                    exect.type(),
                    exect.description(),
                    exect.executor(),
                    exect.commitAfter(),
                    exect.commitBefore(),
                    metrics,
                    mapOutgoingK(exect.outgoing()),
                    mapBoundaryEventsK(exect.boundaryEvents()),
                    exect.extensionProperties(),
                    mapLayout(exect.layout()),
                    exect.retryPolicy()
            );
        }else {
            throw new IllegalArgumentException("Tipo de nó não suportado no mapper: " + dto.getClass().getSimpleName());
        }
    }
}