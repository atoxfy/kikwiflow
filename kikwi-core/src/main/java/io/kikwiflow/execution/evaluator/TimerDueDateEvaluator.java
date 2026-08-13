/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.execution.evaluator;

import io.kikwiflow.exception.BadImplementationException;
import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.api.context.EvaluationContext;
import io.kikwiflow.execution.api.resolver.DueDateProviderResolver;
import io.kikwiflow.model.definition.process.elements.TimerDueDateSource;
import io.kikwiflow.model.definition.process.policies.SchedulePolicy;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.navigation.MapEvaluationContextAdapter;

import java.time.Duration;
import java.time.Instant;

public class TimerDueDateEvaluator {

    private final DueDateProviderResolver dueDateProviderResolver;

    public TimerDueDateEvaluator(DueDateProviderResolver dueDateProviderResolver) {
        this.dueDateProviderResolver = dueDateProviderResolver;
    }

    /**
     * Calcula a próxima data de disparo com base na política de agendamento (SchedulePolicy).
     * Retorna null se a política tiver se esgotado (ex: passou de todas as FIXED_DATES, ou o ciclo prestes a
     * ser agendado ultrapassaria {@code maxOccurrences}).
     *
     * @param occurrenceAboutToFire número (1-based) do ciclo que este cálculo está prestes a agendar — 1 para
     *                              o primeiro disparo do timer, N+1 para o reagendamento após o N-ésimo ciclo
     *                              (ver {@code ContinuationService}).
     */
    public Instant calculateNextSchedule(SchedulePolicy policy, int occurrenceAboutToFire) {
        if (policy == null) return null;

        if (policy.maxOccurrences() != null && occurrenceAboutToFire > policy.maxOccurrences()) {
            return null; // laço de recorrência esgotado por contagem — mesmo sinal que FIXED_DATES esgotada
        }

        return switch (policy.type()) {
            case RATE_DURATION ->
                    Instant.now().plus(Duration.parse(policy.expression()));

            case CRON -> {
                yield Instant.now().plus(Duration.parse(policy.expression()));

                /*CronExpression cron = CronExpression.parse(policy.expression());
                ZonedDateTime next = cron.next(ZonedDateTime.now(ZoneId.of("UTC")));
                yield next != null ? next.toInstant() : null;*/
            }

            case FIXED_DATES -> {
                Instant now = Instant.now();
                yield policy.fixedDates().stream()
                        .map(Instant::parse)
                        .filter(date -> date.isAfter(now))
                        .findFirst()
                        .orElse(null); // Retorna null se não houver mais datas no futuro
            }
        };
    }

    public Instant resolveDueDate(TimerDueDateSource timerDef, ProcessInstanceExecution execution) {
        String timeValue = null;

        switch (timerDef.providerType()) {
            case STATIC -> {
                timeValue = timerDef.staticValue();
            }
            case VARIABLE -> {
                ProcessVariable var = execution.getVariables().get(timerDef.providerVariable());
                if (var != null && var.value() != null) {
                    timeValue = var.value().toString();
                } else {
                    throw new IllegalArgumentException("Kikwiflow Engine: Variável de timer não encontrada -> " + timerDef.providerVariable());
                }
            }
            case BEAN -> {
                if (timerDef.providerBean() == null || timerDef.providerBean().isBlank()) {
                    throw new IllegalArgumentException("Kikwiflow Engine: Nome do Bean não configurado para o timer -> " + timerDef.id());
                }

                EvaluationContext evaluationContext = new MapEvaluationContextAdapter(execution.getId(), execution.getVariables());
                timeValue = dueDateProviderResolver.getProvider(timerDef.providerBean())
                        .map(resolver ->  resolver.resolve(evaluationContext))
                        .orElseThrow(() -> new BadImplementationException("Não foi possível definir a data de execução (dueDate)"));
            }
        }

        if (timeValue == null || timeValue.isBlank()) {
            throw new IllegalArgumentException("Kikwiflow Engine: Valor de tempo resolvido é nulo ou vazio para o timer -> " + timerDef.id());
        }

        return parseDynamicTime(timeValue);
    }

    /**
     * Parse híbrido inteligente: tenta resolver como Data Absoluta, depois como Duração.
     */
    private Instant parseDynamicTime(String timeValue) {
        try {
            return Instant.parse(timeValue);
        } catch (java.time.format.DateTimeParseException e1) {
            try {
                return Instant.now().plus(Duration.parse(timeValue));
            } catch (java.time.format.DateTimeParseException e2) {
                throw new IllegalArgumentException("Formato de tempo inválido: '" + timeValue +
                        "'. Use o formato ISO-8601 absoluto (ex: 2026-12-31T23:59:59Z) ou duração relativa (ex: PT1M).");
            }
        }
    }
}