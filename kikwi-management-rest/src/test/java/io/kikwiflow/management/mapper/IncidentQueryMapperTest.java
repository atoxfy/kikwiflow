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

package io.kikwiflow.management.mapper;

import io.kikwiflow.management.dtos.IncidentSearchRequest;
import io.kikwiflow.persistence.api.query.IncidentQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Dado um IncidentSearchRequest com orderBy")
class IncidentQueryMapperTest {

    @Test
    @DisplayName("Quando orderBy está na whitelist, aplica o campo diretamente ao IncidentQuery")
    void aplicaCampoValido() {
        IncidentQuery query = mock(IncidentQuery.class, Answers.RETURNS_SELF);

        IncidentQueryMapper.applyRequest(query, requestWithOrderBy("createdAt"));

        verify(query).orderBy(eq("createdAt"), eq(false));
    }

    @Test
    @DisplayName("Repassa 'id' como nome de domínio, sem traduzir para '_id'")
    void repassaIdSemTraduzir() {
        IncidentQuery query = mock(IncidentQuery.class, Answers.RETURNS_SELF);

        IncidentQueryMapper.applyRequest(query, requestWithOrderBy("id"));

        verify(query).orderBy(eq("id"), eq(false));
    }

    @Test
    @DisplayName("Quando orderBy não está na whitelist, lança IllegalArgumentException")
    void rejeitaCampoForaDaWhitelist() {
        IncidentQuery query = mock(IncidentQuery.class, Answers.RETURNS_SELF);
        IncidentSearchRequest request = requestWithOrderBy("$where");

        assertThrows(IllegalArgumentException.class, () -> IncidentQueryMapper.applyRequest(query, request));
    }

    @Test
    @DisplayName("Quando orderBy não é informado, não sobrescreve o sort padrão do backend")
    void semOrderByMantemDefaultDoBackend() {
        IncidentQuery query = mock(IncidentQuery.class, Answers.RETURNS_SELF);

        IncidentQueryMapper.applyRequest(query, requestWithOrderBy(null));

        verify(query).orderBy(eq(null), eq(false));
    }

    private static IncidentSearchRequest requestWithOrderBy(String orderBy) {
        return new IncidentSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null,
                orderBy, false, null, null);
    }
}
