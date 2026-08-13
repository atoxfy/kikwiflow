package io.kikwiflow.management.controller.stats.mapper;

import io.kikwiflow.management.dtos.elements.KKFBoundaryEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFCallActivityDefinition;
import io.kikwiflow.management.dtos.elements.KKFEndEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFErrorHandlerDefinition;
import io.kikwiflow.management.dtos.elements.KKFEventCatcherDefinition;
import io.kikwiflow.management.dtos.elements.KKFEventThrowerDefinition;
import io.kikwiflow.management.dtos.elements.KKFExclusiveGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFExecutableTaskDefinition;
import io.kikwiflow.management.dtos.elements.KKFExternalTaskDefinition;
import io.kikwiflow.management.dtos.elements.KKFFlowNodeDefinition;
import io.kikwiflow.management.dtos.elements.KKFInterruptiveCatchEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFInterruptiveTimerEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFJoinGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFNonInterruptiveTimerEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFParallelGatewayDefinition;
import io.kikwiflow.management.dtos.elements.KKFSequenceFlowDefinition;
import io.kikwiflow.management.dtos.elements.KKFStartEventDefinition;
import io.kikwiflow.management.dtos.elements.KKFTimerTaskDefinition;
import io.kikwiflow.management.dtos.layout.KKFLayoutCoordinates;
import io.kikwiflow.model.definition.process.elements.BoundaryEventDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.EndEventDefinition;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.JoinGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.definition.process.layout.LayoutCoordinates;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;
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
                .map(ProcessMapper::mapBoundaryEvent)
                .collect(Collectors.toList());
    }

    // switch exaustivo sobre o sealed interface KKFBoundaryEventDefinition (4 subtipos: nenhum default —
    // um 5º subtipo futuro vira erro de compilação aqui, não um null silencioso como antes. Historicamente só
    // KKFInterruptiveTimerEventDefinition/KKFInterruptiveCatchEventDefinition eram tratados; os outros dois
    // caíam no "return null" sem aviso nenhum.
    private static BoundaryEventDefinition mapBoundaryEvent(KKFBoundaryEventDefinition dto) {
        return switch (dto) {
            case KKFInterruptiveTimerEventDefinition k -> new InterruptiveTimerEventDefinition(
                    k.id(),
                    k.name(),
                    k.type(),
                    k.description(),
                    k.executor(),
                    k.commitAfter(),
                    k.commitBefore(),
                    mapOutgoing(k.outgoing()),
                    dto.attachedToRef(),
                    k.providerType(),
                    k.providerVariable(),
                    k.providerBean(),
                    k.staticValue(),
                    k.extensionProperties(),
                    mapLayout(k.layout()));

            case KKFNonInterruptiveTimerEventDefinition k -> new NonInterruptiveTimerEventDefinition(
                    k.id(),
                    k.name(),
                    k.type(),
                    k.description(),
                    k.executor(),
                    k.commitAfter(),
                    k.commitBefore(),
                    mapOutgoing(k.outgoing()),
                    dto.attachedToRef(),
                    k.schedulePolicy(),
                    k.extensionProperties(),
                    mapLayout(k.layout()));

            case KKFErrorHandlerDefinition k -> new ErrorHandlerDefinition(
                    k.id(),
                    k.name(),
                    k.type(),
                    k.description(),
                    k.commitAfter(),
                    k.commitBefore(),
                    mapOutgoing(k.outgoing()),
                    dto.attachedToRef(),
                    k.errorCode(),
                    k.extensionProperties(),
                    mapLayout(k.layout()));

            case KKFInterruptiveCatchEventDefinition k -> new InterruptiveCatchEventDefinition(
                    k.id(),
                    k.name(),
                    k.type(),
                    k.description(),
                    k.commitAfter(),
                    k.commitBefore(),
                    mapOutgoing(k.outgoing()),
                    dto.attachedToRef(),
                    k.providerType(),
                    k.providerBean(),
                    k.providerVariable(),
                    k.staticKey(),
                    k.keyPrefix(),
                    k.keySuffix(),
                    k.displayNamePrefix(),
                    k.displayNameSuffix(),
                    k.correlationTemplates(),
                    k.extensionProperties(),
                    mapLayout(k.layout()));
        };
    }

    private static KKFNonInterruptiveTimerEventDefinition mapNonInterruptiveTimerEventDefinition(NonInterruptiveTimerEventDefinition kkfNonInterruptiveTimerEventDefinition) {
        return new KKFNonInterruptiveTimerEventDefinition(
                kkfNonInterruptiveTimerEventDefinition.id(),
                kkfNonInterruptiveTimerEventDefinition.name(),
                kkfNonInterruptiveTimerEventDefinition.type(),
                kkfNonInterruptiveTimerEventDefinition.description(),
                kkfNonInterruptiveTimerEventDefinition.executor(),
                kkfNonInterruptiveTimerEventDefinition.commitAfter(),
                kkfNonInterruptiveTimerEventDefinition.commitBefore(),
                mapOutgoingK(kkfNonInterruptiveTimerEventDefinition.outgoing()),
                kkfNonInterruptiveTimerEventDefinition.attachedToRef(),
                kkfNonInterruptiveTimerEventDefinition.schedulePolicy(),
                kkfNonInterruptiveTimerEventDefinition.extensionProperties(),
                mapLayout(kkfNonInterruptiveTimerEventDefinition.layout()));
    }

    private static KKFInterruptiveTimerEventDefinition mapInterruptiveTimerEventDefinition(InterruptiveTimerEventDefinition kkfInterruptiveTimerEventDefinition) {
            return new KKFInterruptiveTimerEventDefinition(
                    kkfInterruptiveTimerEventDefinition.id(),
                    kkfInterruptiveTimerEventDefinition.name(),
                    kkfInterruptiveTimerEventDefinition.type(),
                    kkfInterruptiveTimerEventDefinition.description(),
                    kkfInterruptiveTimerEventDefinition.executor(),
                    kkfInterruptiveTimerEventDefinition.commitAfter(),
                    kkfInterruptiveTimerEventDefinition.commitBefore(),
                    mapOutgoingK(kkfInterruptiveTimerEventDefinition.outgoing()),
                    kkfInterruptiveTimerEventDefinition.attachedToRef(),
                    kkfInterruptiveTimerEventDefinition.providerType(),
                    kkfInterruptiveTimerEventDefinition.providerVariable(),
                    kkfInterruptiveTimerEventDefinition.providerBean(),
                    kkfInterruptiveTimerEventDefinition.staticValue(),
                    kkfInterruptiveTimerEventDefinition.extensionProperties(),
                    mapLayout(kkfInterruptiveTimerEventDefinition.layout()));
    }

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





    // switch exaustivo sobre o sealed interface FlowNodeDefinition — de propósito sem `default`. Antes disto
    // era uma cadeia if/instanceof que compilava mesmo sem cobrir todos os subtipos e só falhava em runtime
    // (IllegalArgumentException) na primeira instância real que alcançasse um nó não mapeado; isso já aconteceu
    // em produção para EventCatcherDefinition/InterruptiveCatchEventDefinition/EventThrowerDefinition/
    // CallActivityDefinition/TimerTaskDefinition (ver ProcessMapperTest). Com o switch, o próximo tipo de nó
    // adicionado a FlowNodeDefinition vira erro de compilação aqui, não uma quebra de
    // GET /pulse/process-definition/{id}/snapshot em produção. Ver docs/engine/21-...md §3.2.
    public static KKFFlowNodeDefinition mapNode(FlowNodeDefinition dto, KKFMetrics metrics) {
        if (dto == null) return null;

        return switch (dto) {
            case StartEventDefinition s -> new KKFStartEventDefinition(
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

            case EndEventDefinition e -> new KKFEndEventDefinition(
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

            case ExclusiveGatewayDefinition g -> new KKFExclusiveGatewayDefinition(
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

            case ParallelGatewayDefinition g -> new KKFParallelGatewayDefinition(
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

            case JoinGatewayDefinition g -> new KKFJoinGatewayDefinition(
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

            case ExternalTaskDefinition t -> new KKFExternalTaskDefinition(
                    t.id(),
                    t.name(),
                    t.type(),
                    t.description(),
                    t.commitAfter(),
                    t.commitBefore(),
                    metrics,
                    mapOutgoingK(t.outgoing()),
                    t.boundaryEventIds(),
                    t.extensionProperties(),
                    mapLayout(t.layout())
            );

            case InterruptiveTimerEventDefinition interruptiveTimerEventDefinition ->
                    mapInterruptiveTimerEventDefinition(interruptiveTimerEventDefinition);

            case NonInterruptiveTimerEventDefinition nonInterruptiveTimerEventDefinition ->
                    mapNonInterruptiveTimerEventDefinition(nonInterruptiveTimerEventDefinition);

            case ErrorHandlerDefinition errorHandlerDefinition -> mapErrorHandlerDefinition(errorHandlerDefinition);

            case ExecutableTaskDefinition exect -> new KKFExecutableTaskDefinition(
                    exect.id(),
                    exect.name(),
                    exect.type(),
                    exect.description(),
                    exect.executor(),
                    exect.commitAfter(),
                    exect.commitBefore(),
                    metrics,
                    mapOutgoingK(exect.outgoing()),
                    exect.boundaryEventIds(),
                    exect.extensionProperties(),
                    mapLayout(exect.layout()),
                    exect.retryPolicy()
            );

            case EventCatcherDefinition eventCatcher -> mapEventCatcherDefinition(eventCatcher, metrics);

            case InterruptiveCatchEventDefinition interruptiveCatchEvent ->
                    mapInterruptiveCatchEventDefinition(interruptiveCatchEvent);

            case EventThrowerDefinition eventThrower -> mapEventThrowerDefinition(eventThrower);

            case CallActivityDefinition callActivity -> mapCallActivityDefinition(callActivity, metrics);

            case TimerTaskDefinition timerTask -> mapTimerTaskDefinition(timerTask, metrics);
        };
    }

    private static KKFEventCatcherDefinition mapEventCatcherDefinition(EventCatcherDefinition eventCatcher, KKFMetrics metrics) {
        return new KKFEventCatcherDefinition(
                eventCatcher.id(),
                eventCatcher.name(),
                eventCatcher.type(),
                eventCatcher.description(),
                eventCatcher.commitAfter(),
                eventCatcher.commitBefore(),
                metrics,
                mapOutgoingK(eventCatcher.outgoing()),
                eventCatcher.boundaryEventIds(),
                eventCatcher.catchType(),
                eventCatcher.providerType(),
                eventCatcher.providerBean(),
                eventCatcher.providerVariable(),
                eventCatcher.staticKey(),
                eventCatcher.keyPrefix(),
                eventCatcher.keySuffix(),
                eventCatcher.displayNamePrefix(),
                eventCatcher.displayNameSuffix(),
                eventCatcher.matchPolicy(),
                eventCatcher.correlationTemplates(),
                eventCatcher.extensionProperties(),
                mapLayout(eventCatcher.layout()));
    }

    private static KKFInterruptiveCatchEventDefinition mapInterruptiveCatchEventDefinition(InterruptiveCatchEventDefinition interruptiveCatchEvent) {
        return new KKFInterruptiveCatchEventDefinition(
                interruptiveCatchEvent.id(),
                interruptiveCatchEvent.name(),
                interruptiveCatchEvent.type(),
                interruptiveCatchEvent.description(),
                interruptiveCatchEvent.commitAfter(),
                interruptiveCatchEvent.commitBefore(),
                mapOutgoingK(interruptiveCatchEvent.outgoing()),
                interruptiveCatchEvent.attachedToRef(),
                interruptiveCatchEvent.providerType(),
                interruptiveCatchEvent.providerBean(),
                interruptiveCatchEvent.providerVariable(),
                interruptiveCatchEvent.staticKey(),
                interruptiveCatchEvent.keyPrefix(),
                interruptiveCatchEvent.keySuffix(),
                interruptiveCatchEvent.displayNamePrefix(),
                interruptiveCatchEvent.displayNameSuffix(),
                interruptiveCatchEvent.correlationTemplates(),
                interruptiveCatchEvent.extensionProperties(),
                mapLayout(interruptiveCatchEvent.layout()));
    }

    private static KKFEventThrowerDefinition mapEventThrowerDefinition(EventThrowerDefinition eventThrower) {
        return new KKFEventThrowerDefinition(
                eventThrower.id(),
                eventThrower.name(),
                eventThrower.type(),
                eventThrower.description(),
                eventThrower.commitAfter(),
                eventThrower.commitBefore(),
                mapOutgoingK(eventThrower.outgoing()),
                eventThrower.providerType(),
                eventThrower.providerBean(),
                eventThrower.providerVariable(),
                eventThrower.staticKey(),
                eventThrower.keyPrefix(),
                eventThrower.keySuffix(),
                eventThrower.displayNamePrefix(),
                eventThrower.displayNameSuffix(),
                eventThrower.correlationTemplates(),
                eventThrower.extensionProperties(),
                mapLayout(eventThrower.layout()));
    }

    private static KKFCallActivityDefinition mapCallActivityDefinition(CallActivityDefinition callActivity, KKFMetrics metrics) {
        return new KKFCallActivityDefinition(
                callActivity.id(),
                callActivity.name(),
                callActivity.type(),
                callActivity.description(),
                callActivity.commitAfter(),
                callActivity.commitBefore(),
                metrics,
                mapOutgoingK(callActivity.outgoing()),
                callActivity.boundaryEventIds(),
                callActivity.calledElement(),
                callActivity.collectionVariable(),
                callActivity.elementVariable(),
                callActivity.iterationMode(),
                callActivity.extensionProperties(),
                mapLayout(callActivity.layout()));
    }

    private static KKFTimerTaskDefinition mapTimerTaskDefinition(TimerTaskDefinition timerTask, KKFMetrics metrics) {
        return new KKFTimerTaskDefinition(
                timerTask.id(),
                timerTask.name(),
                timerTask.type(),
                timerTask.description(),
                timerTask.commitAfter(),
                timerTask.commitBefore(),
                metrics,
                mapOutgoingK(timerTask.outgoing()),
                timerTask.providerType(),
                timerTask.providerVariable(),
                timerTask.providerBean(),
                timerTask.staticValue(),
                timerTask.extensionProperties(),
                mapLayout(timerTask.layout()));
    }

    private static KKFErrorHandlerDefinition mapErrorHandlerDefinition(ErrorHandlerDefinition errorHandlerDefinition) {
        return new KKFErrorHandlerDefinition(
                errorHandlerDefinition.id(),
                errorHandlerDefinition.name(),
                errorHandlerDefinition.type(),
                errorHandlerDefinition.description(),
                errorHandlerDefinition.commitAfter(),
                errorHandlerDefinition.commitBefore(),
                mapOutgoingK(errorHandlerDefinition.outgoing()),
                errorHandlerDefinition.attachedToRef(),
                errorHandlerDefinition.errorCode(),
                errorHandlerDefinition.extensionProperties(),
                mapLayout(errorHandlerDefinition.layout()));
    }
}