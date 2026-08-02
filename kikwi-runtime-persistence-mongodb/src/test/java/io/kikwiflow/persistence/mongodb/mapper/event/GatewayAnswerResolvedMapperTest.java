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

package io.kikwiflow.persistence.mongodb.mapper.event;

import io.kikwiflow.model.event.GatewayAnswerResolved;
import io.kikwiflow.model.execution.enumerated.AnswerProviderType;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayAnswerResolvedMapperTest {

    @Test
    void roundTripsAllFields() {
        Instant evaluatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        GatewayAnswerResolved original = new GatewayAnswerResolved(
                "proc-instance-1",
                "proc-def-1",
                "tenant-a",
                "kyc-async",
                "GATEWAY-CLASSIFICACAO-RISCO",
                AnswerProviderType.BEAN,
                "customerRiskStrategy",
                null,
                "FRAUDE",
                "fraude-flow",
                evaluatedAt
        );

        Document doc = GatewayAnswerResolvedMapper.toDocument(original);
        GatewayAnswerResolved restored = GatewayAnswerResolvedMapper.fromDocument(doc);

        assertEquals(original, restored);
    }

    @Test
    void roundTripsVariableProviderType() {
        GatewayAnswerResolved original = new GatewayAnswerResolved(
                "proc-instance-2",
                "proc-def-1",
                "tenant-a",
                "kyc-async",
                "GATEWAY-ACAO-FRAUDE",
                AnswerProviderType.VARIABLE,
                null,
                "acaoResultadoAnaliseFraude",
                "FINALIZAR",
                "finalizar-flow",
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );

        Document doc = GatewayAnswerResolvedMapper.toDocument(original);
        GatewayAnswerResolved restored = GatewayAnswerResolvedMapper.fromDocument(doc);

        assertEquals(original, restored);
    }
}
