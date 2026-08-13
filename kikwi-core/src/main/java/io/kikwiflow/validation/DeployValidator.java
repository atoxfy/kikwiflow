/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kikwiflow.validation;

import io.kikwiflow.exception.InvalidProcessDefinitionException;
import io.kikwiflow.exception.TaskHandlerNotFoundException;
import io.kikwiflow.execution.TaskHandlerResolver;
import io.kikwiflow.execution.api.provider.AnswerProvider;
import io.kikwiflow.execution.api.resolver.AnswerProviderResolver;
import io.kikwiflow.execution.api.resolver.CorrelationKeysProviderResolver;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.BoundaryEventDefinition;
import io.kikwiflow.model.definition.process.elements.CallActivityDefinition;
import io.kikwiflow.model.definition.process.elements.CorrelationKeySource;
import io.kikwiflow.model.definition.process.elements.ErrorHandlerDefinition;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.ExclusiveGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.ExternalTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;

import java.util.List;
import java.util.Set;

/**
 * Validates a ProcessDefinition at deploy-time to ensure all its required
 * dependencies (e.g., Spring beans for task handlers and rules) are available.
 */
public class DeployValidator {

    private final TaskHandlerResolver taskHandlerResolver;
    private final AnswerProviderResolver answerProviderResolver;
    private final CorrelationKeysProviderResolver correlationKeysProviderResolver;

    public DeployValidator(TaskHandlerResolver taskHandlerResolver, AnswerProviderResolver answerProviderResolver,
                            CorrelationKeysProviderResolver correlationKeysProviderResolver) {
        this.taskHandlerResolver = taskHandlerResolver;
        this.answerProviderResolver = answerProviderResolver;
        this.correlationKeysProviderResolver = correlationKeysProviderResolver;
    }

    public void validate(ProcessDefinition definition) {
        definition.flowNodes().values().forEach(node -> {
            if (node instanceof ExecutableTaskDefinition serviceTask) {
                String executor = serviceTask.executor();
                if (executor != null && !executor.isBlank()) {
                    try {
                        taskHandlerResolver.resolve(executor)
                                .orElseThrow(() -> new TaskHandlerNotFoundException(""));
                    } catch (Exception e) {
                        throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for Service Task '%s' (id: %s): Task Handler bean '%s' not found in application context.",
                                serviceTask.name(), serviceTask.id(), executor), e);
                    }
                }

                // Boundary events interruptivos (timer ou catch event) só podem ser anexados a um nó que não
                // roda handler síncrono com efeito colateral real (WaitState/EXTERNAL_TASK, ou TIMER_TASK — ver
                // validação abaixo). Um EXECUTABLE_TASK executa seu handler de forma síncrona, na mesma thread
                // que o adquiriu — não há como "interrompê-lo" de fora a meio caminho sem risco de um efeito
                // colateral real (ex.: uma chamada de API) já ter acontecido antes da interrupção vencer a
                // corrida de finalização, e sem forma de desfazê-lo depois (ver
                // docs/engine/19-guard-de-finalizacao-boundary-events.md). BOUNDARY_ERROR_HANDLER continua
                // permitido: é try/catch síncrono na mesma call stack, não uma interrupção assíncrona.
                validateBoundaryEvents(definition, serviceTask, "Executable Task", serviceTask.boundaryEventIds(),
                        Set.of(NonInterruptiveTimerEventDefinition.class, ErrorHandlerDefinition.class));
            } else if (node instanceof ExclusiveGatewayDefinition gateway) {
                // Validação do Provedor de Resposta
                if (gateway.providerType() == AnswerProviderType.BEAN) {
                    String beanName = gateway.providerBean();
                    if (beanName == null || beanName.isBlank()) {
                        throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Configured as BEAN but 'providerBean' is empty.", gateway.id()));
                    }
                    try {
                        AnswerProvider provider = answerProviderResolver.getProvider(beanName).orElseThrow(() -> new InvalidProcessDefinitionException(String.format("AnswerProvider bean '%s' not found.", beanName)));

                    } catch (Exception e) {
                        throw new InvalidProcessDefinitionException(String.format("AnswerProvider bean '%s' not found or could not be instantiated.", beanName), e);
                    }
                } else if (gateway.providerType() == AnswerProviderType.VARIABLE) {
                    if (gateway.providerVariable() == null || gateway.providerVariable().isBlank()) {
                        throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Configured as VARIABLE but 'providerVariable' is empty.", gateway.id()));
                    }
                } else {
                    throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': 'providerType' is missing.", gateway.id()));
                }

                // Validação Estrutural das Arestas (Sequence Flows)
                long defaultFlowsCount = gateway.outgoing().stream().filter(SequenceFlowDefinition::isDefault).count();
                if (defaultFlowsCount > 1) {
                    throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Multiple sequence flows are marked as default.", gateway.id()));
                }
            } else if (node instanceof CallActivityDefinition callActivity) {
                // "collectionVariable resolve para uma lista" não é validável em deploy-time: o motor não tem
                // nenhum mecanismo de declaração de tipo de variável de processo — só existe o valor em
                // runtime (ver docs/engine/20-subprocessos-call-activity-especificacao.md, §7). Essa checagem
                // acontece quando o coordenador é de fato alcançado (ContinuationService.generateCallActivityFanOut).
                if (callActivity.calledElement() == null || callActivity.calledElement().isBlank()) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for CALL_ACTIVITY_COORDINATOR '%s' (id: %s): 'calledElement' is empty.",
                                    callActivity.name(), callActivity.id()));
                }
                if (callActivity.elementVariable() != null && !callActivity.elementVariable().isBlank()
                        && (callActivity.collectionVariable() == null || callActivity.collectionVariable().isBlank())) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for CALL_ACTIVITY_COORDINATOR '%s' (id: %s): 'elementVariable' is only valid together with 'collectionVariable'.",
                                    callActivity.name(), callActivity.id()));
                }

                // Antes desta checagem, um boundaryEventId inválido numa CALL_ACTIVITY_COORDINATOR só falhava em
                // runtime (NotImplementedException em ContinuationService.generateBoundaryEvents), quando o
                // coordenador era de fato alcançado — não em deploy-time. Só os dois tipos de timer são
                // suportados aqui (ver docs/engine/20-subprocessos-call-activity-especificacao.md, §5).
                validateBoundaryEvents(definition, callActivity, "CALL_ACTIVITY_COORDINATOR", callActivity.boundaryEventIds(),
                        Set.of(InterruptiveTimerEventDefinition.class, NonInterruptiveTimerEventDefinition.class));
            } else if (node instanceof EventCatcherDefinition eventCatcher) {
                validateCorrelationKeySource(eventCatcher, "EVENT_CATCHER", eventCatcher.name());

                // STATIC sempre resolve exatamente 1 item — não faz sentido em modo GROUP (scatter-gather de N
                // chaves). Ver docs/engine/16-event-catcher-correlacao-de-eventos.md.
                if (eventCatcher.catchType() == CatchType.GROUP && eventCatcher.providerType() == CorrelationProviderType.STATIC) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for EVENT_CATCHER '%s' (id: %s): 'catchType' GROUP with 'providerType' STATIC always resolves a single key — use VARIABLE, BEAN or TEMPLATE instead.",
                                    eventCatcher.name(), eventCatcher.id()));
                }

                // Só os dois tipos de timer de borda são reconhecidos quando anexados a um EVENT_CATCHER
                // (ContinuationService.generateBoundaryEvents) — qualquer outro tipo, incluindo
                // BOUNDARY_INTERRUPTIVE_CATCH_EVENT, não é suportado aqui.
                validateBoundaryEvents(definition, eventCatcher, "EVENT_CATCHER", eventCatcher.boundaryEventIds(),
                        Set.of(InterruptiveTimerEventDefinition.class, NonInterruptiveTimerEventDefinition.class));
            } else if (node instanceof InterruptiveCatchEventDefinition catchEvent) {
                validateCorrelationKeySource(catchEvent, "BOUNDARY_INTERRUPTIVE_CATCH_EVENT", catchEvent.name());

                // EXTERNAL_TASK (WaitState) e TIMER_TASK são os únicos hosts suportados como attachedToRef —
                // nenhum dos dois roda handler síncrono com efeito colateral real. EXECUTABLE_TASK já é
                // bloqueado pelo branch acima (na perspectiva do próprio EXECUTABLE_TASK); EVENT_CATCHER ainda
                // não é suportado (ver docs/engine/17-boundary-interruptive-catch-event.md).
                FlowNodeDefinition attachedTo = definition.flowNodes().get(catchEvent.attachedToRef());
                if (!(attachedTo instanceof ExternalTaskDefinition) && !(attachedTo instanceof TimerTaskDefinition)) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for BOUNDARY_INTERRUPTIVE_CATCH_EVENT '%s' (id: %s): 'attachedToRef' must point to an EXTERNAL_TASK or a TIMER_TASK — got %s.",
                                    catchEvent.name(), catchEvent.id(),
                                    attachedTo != null ? attachedTo.type() : "an unknown node ('" + catchEvent.attachedToRef() + "')"));
                }
            } else if (node instanceof EventThrowerDefinition eventThrower) {
                validateCorrelationKeySource(eventThrower, "EVENT_THROWER", eventThrower.name());
            } else if (node instanceof TimerTaskDefinition timerTask) {
                // Seguro por natureza: TimerTaskDefinition não implementa Executable — não roda handler nenhum,
                // então não há efeito colateral em voo pra proteger (ver Javadoc da própria classe). Aceita os
                // dois tipos de timer de borda e o catch event interruptivo; BOUNDARY_ERROR_HANDLER não faz
                // sentido aqui (não existe try/catch síncrono nenhum pra resolver).
                validateBoundaryEvents(definition, timerTask, "TIMER_TASK", timerTask.boundaryEventIds(),
                        Set.of(InterruptiveTimerEventDefinition.class, NonInterruptiveTimerEventDefinition.class,
                                InterruptiveCatchEventDefinition.class));
            }
        });
    }

    /**
     * Política única de "quais boundary events valem em qual tipo de nó pai" — allowlist por classe de
     * {@link BoundaryEventDefinition}, resolvida em deploy-time em vez de espalhada em runtime. A
     * materialização em si (como cada tipo de boundary event vira uma ExecutableTask/ExternalTask) é
     * igualmente genérica, em {@code ContinuationService.generateBoundaryEvents} — só a política de quais
     * tipos cada host aceita muda por chamada, e agora vive só aqui.
     */
    private void validateBoundaryEvents(ProcessDefinition definition, FlowNodeDefinition hostNode, String hostLabel,
                                        List<String> boundaryEventIds,
                                        Set<Class<? extends BoundaryEventDefinition>> allowedTypes) {
        if (boundaryEventIds == null) {
            return;
        }

        for (String boundaryEventId : boundaryEventIds) {
            FlowNodeDefinition boundaryEvent = definition.flowNodes().get(boundaryEventId);
            boolean allowed = boundaryEvent != null
                    && allowedTypes.stream().anyMatch(allowedType -> allowedType.isInstance(boundaryEvent));

            if (!allowed) {
                throw new InvalidProcessDefinitionException(String.format(
                        "Validation failed for %s '%s' (id: %s): boundary event '%s' (%s) is not supported here. Allowed here: %s.",
                        hostLabel, hostNode.name(), hostNode.id(), boundaryEventId,
                        boundaryEvent != null ? boundaryEvent.type() : "unknown node",
                        allowedTypes.stream().map(Class::getSimpleName).sorted().toList()));
            }
        }
    }

    /**
     * Validação comum aos três nós que implementam {@link CorrelationKeySource} (STATIC/VARIABLE/BEAN/TEMPLATE) —
     * {@link EventCatcherDefinition}, {@link InterruptiveCatchEventDefinition}, {@link EventThrowerDefinition}.
     * Mesma lacuna que existia para o BEAN de {@code AnswerProvider} de um gateway: sem esta checagem, um
     * {@code providerBean} inexistente só falha em runtime, na primeira vez que o nó é alcançado.
     */
    private void validateCorrelationKeySource(CorrelationKeySource source, String nodeKind, String nodeName) {
        CorrelationProviderType providerType = source.providerType();
        if (providerType == null) {
            throw new InvalidProcessDefinitionException(
                    String.format("Validation failed for %s '%s' (id: %s): 'providerType' is missing.", nodeKind, nodeName, source.id()));
        }

        switch (providerType) {
            case STATIC -> {
                if (source.staticKey() == null || source.staticKey().isBlank()) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for %s '%s' (id: %s): Configured as STATIC but 'staticKey' is empty.", nodeKind, nodeName, source.id()));
                }
            }
            case VARIABLE -> {
                if (source.providerVariable() == null || source.providerVariable().isBlank()) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for %s '%s' (id: %s): Configured as VARIABLE but 'providerVariable' is empty.", nodeKind, nodeName, source.id()));
                }
            }
            case BEAN -> {
                String beanName = source.providerBean();
                if (beanName == null || beanName.isBlank()) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for %s '%s' (id: %s): Configured as BEAN but 'providerBean' is empty.", nodeKind, nodeName, source.id()));
                }
                try {
                    correlationKeysProviderResolver.getProvider(beanName)
                            .orElseThrow(() -> new InvalidProcessDefinitionException(String.format("CorrelationKeysProvider bean '%s' not found.", beanName)));
                } catch (Exception e) {
                    throw new InvalidProcessDefinitionException(String.format("CorrelationKeysProvider bean '%s' not found or could not be instantiated.", beanName), e);
                }
            }
            case TEMPLATE -> {
                if (source.correlationTemplates() == null || source.correlationTemplates().isEmpty()) {
                    throw new InvalidProcessDefinitionException(
                            String.format("Validation failed for %s '%s' (id: %s): Configured as TEMPLATE but 'correlationTemplates' is empty.", nodeKind, nodeName, source.id()));
                }
            }
        }
    }
}