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

package io.kikwiflow.model.definition.process.policies;

import io.kikwiflow.model.execution.enumerated.ScheduleType;

import java.util.List;

public record SchedulePolicy(
        ScheduleType type,
        String expression,
        List<String> fixedDates,
        // Teto opcional de ciclos do laço de recorrência de um BOUNDARY_NON_INTERRUPTIVE_TIMER (ver
        // TimerDueDateEvaluator.calculateNextSchedule). Null = comportamento legado, recorrência indefinida
        // (bounded só por RATE_DURATION vencer/FIXED_DATES esgotar, se aplicável). Contagem 1-based: com
        // maxOccurrences = 3, o timer dispara nos ciclos 1, 2 e 3, e o 4º nunca é agendado — útil para réguas
        // de cobrança/lembrete com um número fixo de tentativas, independente de quando o nó pai terminar.
        Integer maxOccurrences
) {}