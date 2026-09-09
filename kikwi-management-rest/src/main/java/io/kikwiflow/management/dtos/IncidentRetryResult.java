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

package io.kikwiflow.management.dtos;

/**
 * Resultado do retry de um incidente dentro de {@code POST /incidents/retry}.
 * {@code status} é {@code "RETRIED"} ou {@code "FAILED"}; {@code code}/{@code message} só são
 * preenchidos em {@code FAILED}, reaproveitando os códigos do envelope de erro padrão
 * ({@code NOT_FOUND}, {@code CONFLICT}).
 */
public record IncidentRetryResult(
        String incidentId,
        String status,
        String code,
        String message
) {
    public static IncidentRetryResult retried(String incidentId) {
        return new IncidentRetryResult(incidentId, "RETRIED", null, null);
    }

    public static IncidentRetryResult failed(String incidentId, String code, String message) {
        return new IncidentRetryResult(incidentId, "FAILED", code, message);
    }
}
