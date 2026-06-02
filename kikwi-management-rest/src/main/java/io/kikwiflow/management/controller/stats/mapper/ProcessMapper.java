package io.kikwiflow.management.controller.stats.mapper;

import io.kikwiflow.management.controller.stats.response.elements.KKFBoundaryEventDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFEndEventDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFExclusiveGatewayDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFExecutableTaskDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFExternalTaskDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFInterruptiveTimerEventDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFSequenceFlowDefinition;
import io.kikwiflow.management.controller.stats.response.elements.KKFStartEventDefinition;
import io.kikwiflow.management.controller.stats.response.layout.KKFLayoutCoordinates;
import io.kikwiflow.model.definition.process.elements.BoundaryEventDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import io.kikwiflow.model.stats.KKFMetrics;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessMapper {


    // Switch Expression do Java moderno para mapear os tipos
    private static FlowNodeDefinition mapNode(KKFFlowNodeDefinition dto) {
        if (dto == null) return null;

        if(dto instanceof KKFStartEventDefinition) {
            KKFStartEventDefinition s = (KKFStartEventDefinition) dto;
            return new StartEventDefinition(
                    s.id(),
                    s.name(),
                    s.description(),
                    s.type(),
                    s.commitAfter(),
                    s.commitBefore(),
                    mapOutgoing(s.outgoing()),
                    s.extensionProperties(),
                    mapLayout(s.layout())
            );
        } else if (dto instanceof KKFEndEventDefinition) {
            KKFEndEventDefinition e = (KKFEndEventDefinition) dto;
            return new EndEventDefinition(
                    e.id(),
                    e.name(),
                    e.description(),
                    e.type(),
                    e.commitAfter(),
                    e.commitBefore(),
                    null,
                    e.extensionProperties(),
                    mapLayout(e.layout())
            );
        } else if (dto instanceof KKFExclusiveGatewayDefinition) {
            KKFExclusiveGatewayDefinition g = (KKFExclusiveGatewayDefinition) dto;
            return new ExclusiveGatewayDefinition(
                    g.id(),
                    g.name(),
                    g.type(),
                    g.description(),
                    g.commitAfter(),
                    g.commitBefore(),
                    g.defaultFlow(),
                    mapOutgoing(g.outgoing()),
                    g.extensionProperties(),
                    mapLayout(g.layout())
            );
        } else if (dto instanceof KKFExternalTaskDefinition) {
            KKFExternalTaskDefinition t = (KKFExternalTaskDefinition) dto;
            return new ExternalTaskDefinition(
                    t.id(),
                    t.name(),
                    t.type(),
                    t.description(),
                    t.commitAfter(),
                    t.commitBefore(),
                    mapOutgoing(t.outgoing()),
                    mapBoundaryEvents(t.boundaryEvents()),
                    t.extensionProperties(),
                    mapLayout(t.layout())
            );
        }else if (dto instanceof KKFExecutableTaskDefinition) {
            KKFExecutableTaskDefinition exect = (KKFExecutableTaskDefinition) dto;
            return new ExecutableTaskDefinition(
                    exect.id(),
                    exect.name(),
                    exect.type(),
                    exect.description(),
                    exect.executor() ,
                    exect.commitAfter(),
                    exect.commitBefore(),
                    mapOutgoing(exect.outgoing()),
                    mapBoundaryEvents(exect.boundaryEvents()),
                    exect.extensionProperties(),
                    mapLayout(exect.layout())
            );
        }else {
           throw new IllegalArgumentException("Tipo de nó não suportado no mapper: " + dto.getClass().getSimpleName());
        }
    }


    // Helper para converter Listas de Fluxo
    private static List<SequenceFlowDefinition> mapOutgoing(List<KKFSequenceFlowDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(dto -> new SequenceFlowDefinition(
                dto.id(),
                dto.condition(),
                dto.targetNodeId(),
                dto.isDefault() != null ? dto.isDefault() : false,
                mapPositionHandlers(dto.positionHandlers())
        )).collect(Collectors.toList());
    }


    private static List<KKFSequenceFlowDefinition> mapOutgoingK(List<SequenceFlowDefinition> dtos) {
        if (dtos == null) return Collections.emptyList();
        return dtos.stream().map(dto -> new KKFSequenceFlowDefinition(
                dto.id(),
                dto.condition(),
                dto.targetNodeId(),
                dto.isDefault() != null ? dto.isDefault() : false,
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
                                kkfInterruptiveTimerEventDefinition.delegateExpression(),
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
                    ProcessMapper.mapLayout(g.layout())
            );
        } else if (dto instanceof ExternalTaskDefinition t) {
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
                    mapLayout(exect.layout())
            );
        }else {
            throw new IllegalArgumentException("Tipo de nó não suportado no mapper: " + dto.getClass().getSimpleName());
        }
    }
}