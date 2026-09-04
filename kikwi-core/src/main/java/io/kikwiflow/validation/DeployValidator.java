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
import io.kikwiflow.model.definition.process.elements.JoinGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.NonInterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.elements.ParallelGatewayDefinition;
import io.kikwiflow.model.definition.process.elements.SequenceFlowDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.elements.TimerTaskDefinition;
import io.kikwiflow.model.definition.process.policies.RetryPolicy;
import io.kikwiflow.model.definition.process.policies.SchedulePolicy;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.CorrelationProviderType;
import io.kikwiflow.model.execution.enumerated.RetryStrategy;
import io.kikwiflow.model.execution.enumerated.ScheduleType;
import io.kikwiflow.model.execution.enumerated.TimeProviderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates a ProcessDefinition at deploy-time to ensure all its required
 * dependencies (e.g., Spring beans for task handlers and rules) are available.
 * <p>
 * As regras implementadas aqui espelham o catálogo normativo em
 * docs/engine/14-regras-de-processo-valido.md (identificadores {@code KIKWI-NNN}, citados nos comentários
 * abaixo) — só as marcadas como severidade Bloqueante foram promovidas de "Sugerida" para "Existente" nesta
 * revisão; regras de severidade Aviso e as que exigem travessia de grafo com risco de falso-positivo
 * (KIKWI-005, KIKWI-029) ficam de fora deliberadamente. Ver docs/engine/15-achados-motor-lacunas-de-validacao.md
 * para o racional completo de cada uma.
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
        validateDefaultStartPoint(definition);
        validateSequenceFlowTargetsExist(definition);

        definition.flowNodes().values().forEach(node -> {
            if (node instanceof StartEventDefinition startEvent) {
                // KIKWI-009 (Bloqueante): sem exatamente 1 saída, o processo inicia e encerra ali mesmo,
                // silenciosamente (Navigator.determineNextContinuation trata outgoing vazio como fim de fluxo
                // pra qualquer tipo de nó — ver docs/engine/15-achados-motor-lacunas-de-validacao.md, §2.3).
                int outgoingCount = startEvent.outgoing() == null ? 0 : startEvent.outgoing().size();
                if (outgoingCount != 1) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for DEFAULT_START_EVENT '%s' (id: %s): must declare exactly one outgoing sequence flow (found %d).",
                            startEvent.name(), startEvent.id(), outgoingCount));
                }

            } else if (node instanceof ExecutableTaskDefinition serviceTask) {
                String executor = serviceTask.executor();

                // KIKWI-011 (Bloqueante): sem executor, a tarefa não executa nada — hoje isso só falhava em
                // runtime (BadDefinitionExecutionException), na primeira vez que o nó era alcançado.
                if (executor == null || executor.isBlank()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for Executable Task '%s' (id: %s): 'executor' is empty.",
                            serviceTask.name(), serviceTask.id()));
                }

                // KIKWI-012 (já Existente): o bean TaskHandler precisa existir no contexto Spring.
                try {
                    taskHandlerResolver.resolve(executor)
                            .orElseThrow(() -> new TaskHandlerNotFoundException(""));
                } catch (Exception e) {
                    throw new InvalidProcessDefinitionException(
                        String.format("Validation failed for Service Task '%s' (id: %s): Task Handler bean '%s' not found in application context.",
                            serviceTask.name(), serviceTask.id(), executor), e);
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

                // KIKWI-042 (Bloqueante): sem isso, Navigator.findMatchingErrorHandler usa .findFirst() — o
                // primeiro handler cujo errorCode bate (ou o primeiro curinga) silenciosamente vence, e os
                // demais nunca são alcançáveis, sem nenhum aviso.
                validateNoDuplicateErrorCodes(definition, serviceTask, "Executable Task", serviceTask.boundaryEventIds());

                // KIKWI-043/044/045 (Bloqueante): RetryPolicy.maxRetries é int primitivo — declarar retryPolicy
                // sem maxRetries explícito deserializa como 0 (não herda o fallback global de 3), e a tarefa
                // abre incidente já na primeira falha, sem nenhuma tentativa adicional (ver
                // docs/engine/15-achados-motor-lacunas-de-validacao.md, §4.5).
                RetryPolicy retryPolicy = serviceTask.retryPolicy();
                if (retryPolicy != null) {
                    if (retryPolicy.maxRetries() < 1) {
                        throw new InvalidProcessDefinitionException(String.format(
                                "Validation failed for Executable Task '%s' (id: %s): 'retryPolicy.maxRetries' must be >= 1 (declaring retryPolicy without an explicit maxRetries deserializes to 0 — zero retries, not the global default).",
                                serviceTask.name(), serviceTask.id()));
                    }
                    if (retryPolicy.strategy() == null) {
                        throw new InvalidProcessDefinitionException(String.format(
                                "Validation failed for Executable Task '%s' (id: %s): 'retryPolicy.strategy' is missing — must be LINEAR or EXPONENTIAL_BACKOFF.",
                                serviceTask.name(), serviceTask.id()));
                    }
                    if (retryPolicy.strategy() == RetryStrategy.EXPONENTIAL_BACKOFF
                            && (retryPolicy.initialInterval() == null || retryPolicy.initialInterval().isBlank())) {
                        throw new InvalidProcessDefinitionException(String.format(
                                "Validation failed for Executable Task '%s' (id: %s): 'retryPolicy.strategy' is EXPONENTIAL_BACKOFF but 'initialInterval' is empty.",
                                serviceTask.name(), serviceTask.id()));
                    }
                }

            } else if (node instanceof ExternalTaskDefinition externalTask) {
                // Fecha uma lacuna real (relacionada a KIKWI-041): antes desta checagem, EXTERNAL_TASK era o
                // único host de boundary event sem NENHUMA validação de deploy — anexar um
                // BOUNDARY_ERROR_HANDLER (não suportado aqui, só em EXECUTABLE_TASK) passava limpo e só quebrava
                // com NotImplementedException na primeira instância real (ver
                // docs/engine/15-achados-motor-lacunas-de-validacao.md, §3.7).
                validateBoundaryEvents(definition, externalTask, "External Task", externalTask.boundaryEventIds(),
                        Set.of(InterruptiveTimerEventDefinition.class, NonInterruptiveTimerEventDefinition.class,
                                InterruptiveCatchEventDefinition.class));

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

                // KIKWI-021 (Bloqueante): sem nenhuma saída, o AnswerProvider nunca chega a ser avaliado — o
                // motor encerra o fluxo antes disso (outgoing vazio termina o nó pra qualquer tipo, ver
                // docs/engine/15-achados-motor-lacunas-de-validacao.md, §2.3).
                if (gateway.outgoing() == null || gateway.outgoing().isEmpty()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for Gateway '%s': must declare at least one outgoing sequence flow.", gateway.id()));
                }

                // Validação Estrutural das Arestas (Sequence Flows) — KIKWI-018 (já Existente)
                long defaultFlowsCount = gateway.outgoing().stream().filter(SequenceFlowDefinition::isDefault).count();
                if (defaultFlowsCount > 1) {
                    throw new InvalidProcessDefinitionException(String.format("Validation failed for Gateway '%s': Multiple sequence flows are marked as default.", gateway.id()));
                }

                // KIKWI-019 (Bloqueante): mesmo racional de KIKWI-018 — findMatchingFlow usa .findFirst() sobre
                // handlesNull, então mais de uma aresta marcada vira dead code silencioso, não erro.
                long handlesNullCount = gateway.outgoing().stream().filter(SequenceFlowDefinition::handlesNull).count();
                if (handlesNullCount > 1) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for Gateway '%s': multiple sequence flows are marked as handlesNull — at most one is allowed.", gateway.id()));
                }

                // KIKWI-020 (Bloqueante): mesmo racional — findMatchingFlow usa .findFirst() sobre
                // expectedAnswer, então respostas duplicadas silenciosamente escondem a segunda aresta.
                Set<String> seenAnswers = new java.util.HashSet<>();
                for (SequenceFlowDefinition flow : gateway.outgoing()) {
                    String expectedAnswer = flow.expectedAnswer();
                    if (expectedAnswer != null && !seenAnswers.add(expectedAnswer)) {
                        throw new InvalidProcessDefinitionException(String.format(
                                "Validation failed for Gateway '%s': duplicate expectedAnswer '%s' across outgoing sequence flows.", gateway.id(), expectedAnswer));
                    }
                }

            } else if (node instanceof ParallelGatewayDefinition parallelGateway) {
                // KIKWI-024/025/026 (Bloqueante): sem targetJoinId válido apontando pra um JOIN_GATEWAY real, o
                // split ou lança RuntimeException("Não foi encontrado join") em runtime (targetJoinId nulo), ou
                // degrada silenciosamente — targetJoinId apontando pra um id inexistente/de tipo errado nunca é
                // checado hoje, e o "join" materializado referencia a definição errada (ver
                // docs/engine/15-achados-motor-lacunas-de-validacao.md, §3.6, bloco ":::danger").
                String targetJoinId = parallelGateway.targetJoinId();
                if (targetJoinId == null || targetJoinId.isBlank()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for PARALLEL_GATEWAY '%s' (id: %s): 'targetJoinId' is missing.",
                            parallelGateway.name(), parallelGateway.id()));
                }
                FlowNodeDefinition targetJoin = definition.flowNodes().get(targetJoinId);
                if (targetJoin == null) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for PARALLEL_GATEWAY '%s' (id: %s): 'targetJoinId' ('%s') does not reference an existing node in flowNodes.",
                            parallelGateway.name(), parallelGateway.id(), targetJoinId));
                }
                if (!(targetJoin instanceof JoinGatewayDefinition)) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for PARALLEL_GATEWAY '%s' (id: %s): 'targetJoinId' ('%s') must reference a JOIN_GATEWAY node, but it is %s.",
                            parallelGateway.name(), parallelGateway.id(), targetJoinId, targetJoin.type()));
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
            } else if (node instanceof InterruptiveTimerEventDefinition interruptiveTimer) {
                // KIKWI-033/034/035/036 (Bloqueante): mesma forma condicional já usada no ExclusiveGateway/
                // CorrelationKeySource acima, agora para o timer de borda interruptivo — sem isso, um
                // providerType/staticValue/providerVariable/providerBean mal configurado só falha em runtime, em
                // TimerDueDateEvaluator, quando o timer é efetivamente instanciado (ver
                // docs/engine/15-achados-motor-lacunas-de-validacao.md, §4.3).
                validateTimeProvider(interruptiveTimer.id(), interruptiveTimer.providerType(), interruptiveTimer.providerVariable(),
                        interruptiveTimer.providerBean(), interruptiveTimer.staticValue(), "BOUNDARY_INTERRUPTIVE_TIMER");
            } else if (node instanceof NonInterruptiveTimerEventDefinition nonInterruptiveTimer) {
                // KIKWI-037/039/040 (Bloqueante) + o resquício de KIKWI-038 (schedulePolicy.type não-nulo — o
                // próprio valor já é garantido pelo enum ScheduleType, que não tem mais CRON). Sem schedulePolicy,
                // calculateNextSchedule(null) retorna null imediatamente — o boundary event nunca é criado, sem
                // erro nenhum (degradação silenciosa, ver docs/engine/15-achados-motor-lacunas-de-validacao.md,
                // §1.3).
                SchedulePolicy schedulePolicy = nonInterruptiveTimer.schedulePolicy();
                if (schedulePolicy == null) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for BOUNDARY_NON_INTERRUPTIVE_TIMER '%s' (id: %s): 'schedulePolicy' is missing.",
                            nonInterruptiveTimer.name(), nonInterruptiveTimer.id()));
                }
                if (schedulePolicy.type() == null) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for BOUNDARY_NON_INTERRUPTIVE_TIMER '%s' (id: %s): 'schedulePolicy.type' is missing.",
                            nonInterruptiveTimer.name(), nonInterruptiveTimer.id()));
                }
                if (schedulePolicy.type() == ScheduleType.RATE_DURATION
                        && (schedulePolicy.expression() == null || schedulePolicy.expression().isBlank())) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for BOUNDARY_NON_INTERRUPTIVE_TIMER '%s' (id: %s): Configured as RATE_DURATION but 'expression' is empty.",
                            nonInterruptiveTimer.name(), nonInterruptiveTimer.id()));
                }
                if (schedulePolicy.type() == ScheduleType.FIXED_DATES
                        && (schedulePolicy.fixedDates() == null || schedulePolicy.fixedDates().isEmpty())) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for BOUNDARY_NON_INTERRUPTIVE_TIMER '%s' (id: %s): Configured as FIXED_DATES but 'fixedDates' is empty.",
                            nonInterruptiveTimer.name(), nonInterruptiveTimer.id()));
                }
            }
        });
    }

    /**
     * KIKWI-003 (Bloqueante): sem isso, um {@code defaultStartPoint} nulo/vazio/inexistente só falha quando
     * alguém tenta INICIAR uma instância ({@code KikwiflowEngine.ProcessStarter.execute()} lança
     * {@code NullPointerException}), não no deploy — o processo fica implantado, mas nunca é iniciável.
     */
    private void validateDefaultStartPoint(ProcessDefinition definition) {
        String defaultStartPoint = definition.defaultStartPoint();
        if (defaultStartPoint == null || defaultStartPoint.isBlank()) {
            throw new InvalidProcessDefinitionException(
                    "Validation failed: 'defaultStartPoint' is empty — every process needs a declared entry point.");
        }
        if (!definition.flowNodes().containsKey(defaultStartPoint)) {
            throw new InvalidProcessDefinitionException(String.format(
                    "Validation failed: 'defaultStartPoint' ('%s') does not reference an existing node in flowNodes.",
                    defaultStartPoint));
        }
    }

    /**
     * KIKWI-001 (Bloqueante) — a checagem estática de maior valor do catálogo inteiro: o mesmo erro de
     * modelagem (uma aresta apontando pra um id que não existe) produz sintomas completamente diferentes
     * dependendo de onde acontece hoje — de um {@code NullPointerException} obscuro no próximo ciclo do
     * {@code ProcessExecutionManager} até um ramo de {@code PARALLEL_GATEWAY} silenciosamente descartado — e o
     * motor não valida isso de forma consistente em lugar nenhum (ver
     * docs/engine/15-achados-motor-lacunas-de-validacao.md, §2.1).
     */
    private void validateSequenceFlowTargetsExist(ProcessDefinition definition) {
        definition.flowNodes().forEach((nodeKey, node) -> {
            if (node.outgoing() == null) {
                return;
            }
            for (SequenceFlowDefinition flow : node.outgoing()) {
                if (!definition.flowNodes().containsKey(flow.targetNodeId())) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for node '%s' (id: %s): outgoing sequence flow '%s' targets '%s', which does not exist in flowNodes.",
                            node.name(), nodeKey, flow.id(), flow.targetNodeId()));
                }
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
     * KIKWI-042 (Bloqueante): {@code Navigator.findMatchingErrorHandler} usa {@code .findFirst()} sobre
     * {@code boundaryEventIds} — mais de um handler com o mesmo {@code errorCode} (ou mais de um curinga sem
     * {@code errorCode}) anexado ao mesmo nó pai faz o primeiro que casa vencer silenciosamente, matando os
     * demais sem nenhum aviso. Só relevante para {@code EXECUTABLE_TASK}, o único host onde
     * {@code BOUNDARY_ERROR_HANDLER} é sequer permitido pelo allowlist.
     */
    private void validateNoDuplicateErrorCodes(ProcessDefinition definition, FlowNodeDefinition hostNode, String hostLabel,
                                               List<String> boundaryEventIds) {
        if (boundaryEventIds == null) {
            return;
        }

        Set<String> seenErrorCodes = new java.util.HashSet<>();
        boolean sawWildcard = false;
        for (String boundaryEventId : boundaryEventIds) {
            FlowNodeDefinition boundaryEvent = definition.flowNodes().get(boundaryEventId);
            if (!(boundaryEvent instanceof ErrorHandlerDefinition handler)) {
                continue;
            }

            String errorCode = handler.errorCode();
            if (errorCode == null) {
                if (sawWildcard) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for %s '%s' (id: %s): more than one wildcard BOUNDARY_ERROR_HANDLER (no errorCode) is attached — at most one is allowed.",
                            hostLabel, hostNode.name(), hostNode.id()));
                }
                sawWildcard = true;
            } else if (!seenErrorCodes.add(errorCode)) {
                throw new InvalidProcessDefinitionException(String.format(
                        "Validation failed for %s '%s' (id: %s): duplicate BOUNDARY_ERROR_HANDLER errorCode '%s' — each errorCode may only be handled once per parent node.",
                        hostLabel, hostNode.name(), hostNode.id(), errorCode));
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

    /**
     * Validação condicional compartilhada por qualquer {@link io.kikwiflow.model.definition.process.elements.TimerDueDateSource}
     * (hoje, só {@link InterruptiveTimerEventDefinition} — o timer de borda interruptivo; {@code TimerTaskDefinition}
     * continua deliberadamente fora, ver KIKWI-033 no catálogo). Mesmo formato condicional de
     * {@code validateCorrelationKeySource}/gateway acima: sem isso, um {@code providerType}/valor correspondente
     * mal configurado só falha em runtime, em {@code TimerDueDateEvaluator}, quando o timer é efetivamente
     * instanciado.
     */
    private void validateTimeProvider(String nodeId, TimeProviderType providerType, String providerVariable,
                                      String providerBean, String staticValue, String nodeKind) {
        if (providerType == null) {
            throw new InvalidProcessDefinitionException(String.format(
                    "Validation failed for %s (id: %s): 'providerType' is missing.", nodeKind, nodeId));
        }

        switch (providerType) {
            case STATIC -> {
                if (staticValue == null || staticValue.isBlank()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for %s (id: %s): Configured as STATIC but 'staticValue' is empty.", nodeKind, nodeId));
                }
            }
            case VARIABLE -> {
                if (providerVariable == null || providerVariable.isBlank()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for %s (id: %s): Configured as VARIABLE but 'providerVariable' is empty.", nodeKind, nodeId));
                }
            }
            case BEAN -> {
                if (providerBean == null || providerBean.isBlank()) {
                    throw new InvalidProcessDefinitionException(String.format(
                            "Validation failed for %s (id: %s): Configured as BEAN but 'providerBean' is empty.", nodeKind, nodeId));
                }
            }
        }
    }
}
