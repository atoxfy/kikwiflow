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

import io.kikwiflow.management.dtos.ProcessInstanceSearchRequest;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Antes deste teste existir, {@code orderBy} ia direto do request do cliente para
 * {@code Sorts.ascending/descending(field)} no Mongo, sem nenhuma validação — qualquer nome de campo era aceito.
 * Ver docs/engine/21-revisao-observabilidade-e-performance-monitor.md §4.4.
 */
@DisplayName("Dado um ProcessInstanceSearchRequest com orderBy")
class ProcessInstanceQueryMapperTest {

    @Nested
    @DisplayName("Quando orderBy é um campo da whitelist")
    class CamposValidos {

        @Test
        @DisplayName("aplica o campo diretamente ao ProcessInstanceQuery")
        void aplicaCampoValido() {
            ProcessInstanceQuery query = mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);

            ProcessInstanceQueryMapper.applyRequest(query, requestWithOrderBy("businessKey"));

            verify(query).orderBy(eq("businessKey"), eq(false));
        }

        @Test
        @DisplayName("repassa 'id' como nome de domínio, sem traduzir para '_id' — quem faz isso é cada ProcessInstanceQuery")
        void repassaIdSemTraduzir() {
            ProcessInstanceQuery query = mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);

            ProcessInstanceQueryMapper.applyRequest(query, requestWithOrderBy("id"));

            // Não é "_id": traduzir aqui quebraria o backend in-memory (AssertableKikwiEngine/testes do motor),
            // cujo comparatorFor espera o campo literal "id". A tradução para "_id" é interna ao
            // MongoProcessInstanceQuery.
            verify(query).orderBy(eq("id"), eq(false));
        }
    }

    @Test
    @DisplayName("Quando orderBy não está na whitelist, então lança IllegalArgumentException em vez de repassar ao backend")
    void rejeitaCampoForaDaWhitelist() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);
        ProcessInstanceSearchRequest request = requestWithOrderBy("$where");

        assertThrows(IllegalArgumentException.class, () -> ProcessInstanceQueryMapper.applyRequest(query, request));
    }

    @Test
    @DisplayName("Quando orderBy não é informado, então não sobrescreve o sort padrão do backend")
    void semOrderByMantemDefaultDoBackend() {
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);

        ProcessInstanceQueryMapper.applyRequest(query, requestWithOrderBy(null));

        verify(query).orderBy(eq(null), eq(false));
    }

    private static ProcessInstanceSearchRequest requestWithOrderBy(String orderBy) {
        return new ProcessInstanceSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                orderBy, false, null, null);
    }
}
