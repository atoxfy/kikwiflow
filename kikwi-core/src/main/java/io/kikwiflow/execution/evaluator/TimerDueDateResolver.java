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

import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.model.definition.process.elements.InterruptiveTimerEventDefinition;
import io.kikwiflow.model.definition.process.policies.SchedulePolicy;
import io.kikwiflow.model.execution.ProcessVariable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import static io.kikwiflow.model.execution.enumerated.ScheduleType.RATE_DURATION;

public class TimerDueDateResolver {

    // private final DueDateProviderLocator beanLocator;

    public TimerDueDateResolver(/* DueDateProviderLocator beanLocator */) {
        // this.beanLocator = beanLocator;
    }

    /**
     * Calcula a próxima data de disparo com base na política de agendamento (SchedulePolicy).
     * Retorna null se a política tiver se esgotado (ex: passou de todas as FIXED_DATES).
     */
    public Instant calculateNextSchedule(SchedulePolicy policy) {
        if (policy == null) return null;

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

    public Instant resolveDueDate(InterruptiveTimerEventDefinition timerDef, ProcessInstanceExecution execution) {
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
                // timeValue = beanLocator.getProvider(timerDef.providerBean()).resolve(execution);
                throw new UnsupportedOperationException("Resolução de timer via BEAN ainda não implementada.");
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