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

import io.kikwiflow.model.event.ExternalTaskCompleted;
import io.kikwiflow.model.execution.ProcessVariable;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalTaskCompletedMapperTest {

    @Test
    void roundTripsAllFields() {
        ExternalTaskCompleted original = new ExternalTaskCompleted(
                "external-task-1",
                "proc-def-1",
                "proc-instance-1",
                "tenant-a",
                "APPROVE_ORDER_MT",
                "worker-1",
                "supervisor-1",
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                // chave com ponto: exercita o escape de MongoKeyEncoder
                Map.of("decisao", new ProcessVariable("decisao", "APROVAR"),
                        "cliente.cpf", new ProcessVariable("cliente.cpf", "757.491.186-06"))
        );

        Document doc = ExternalTaskCompletedMapper.toDocument(original);
        ExternalTaskCompleted restored = ExternalTaskCompletedMapper.fromDocument(doc);

        assertEquals(original, restored);
    }

    @Test
    void roundTripsWithNullAssignee() {
        ExternalTaskCompleted original = new ExternalTaskCompleted(
                "external-task-2",
                "proc-def-1",
                "proc-instance-2",
                "tenant-a",
                "APPROVE_ORDER_MT",
                null,
                "worker-2",
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                Map.of()
        );

        Document doc = ExternalTaskCompletedMapper.toDocument(original);
        ExternalTaskCompleted restored = ExternalTaskCompletedMapper.fromDocument(doc);

        assertEquals(original, restored);
    }

    @Test
    void readsDocumentWrittenBeforeSubmittedVariablesExisted() {
        Document legacy = ExternalTaskCompletedMapper.toDocument(new ExternalTaskCompleted("external-task-3", "proc-def-1",
                "proc-instance-3", null, "APPROVE_ORDER_MT", null, "worker-3", Instant.now().truncatedTo(ChronoUnit.MILLIS), Map.of()));
        legacy.remove("submittedVariables");

        assertEquals(Map.of(), ExternalTaskCompletedMapper.fromDocument(legacy).submittedVariables());
    }
}
