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

package io.kikwiflow.persistence.mongodb.mapper;

import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.node.ExternalTask;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExternalTaskMapperTest {

    @Test
    void roundTripsStandardTaskWithoutEventCatcherFields() {
        ExternalTask original = ExternalTask.builder()
                .id("task-1")
                .name("Human Task")
                .taskDefinitionId("HUMAN_TASK")
                .processInstanceId("proc-1")
                .processDefinitionId("def-1")
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MILLIS))
                .tenantId("tenant-a")
                .build();

        ExternalTask restored = ExternalTaskMapper.fromDocument(ExternalTaskMapper.toDocument(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.createdAt(), restored.createdAt());
        assertNull(restored.type());
        assertNull(restored.correlationKey());
        assertNull(restored.coordinatorTaskId());
    }

    @Test
    void roundTripsEventCatcherStandaloneFields() {
        ExternalTask original = ExternalTask.builder()
                .id("task-2")
                .taskDefinitionId("WAIT_ORDER_PAID")
                .processInstanceId("proc-2")
                .processDefinitionId("def-1")
                .tenantId("tenant-a")
                .type(ExternalTaskType.EVENT_CATCHER_STANDALONE)
                .correlationKey("ORDER_9988_PAID")
                .displayName("Pagamento do pedido 9988")
                .build();

        ExternalTask restored = ExternalTaskMapper.fromDocument(ExternalTaskMapper.toDocument(original));

        assertEquals(ExternalTaskType.EVENT_CATCHER_STANDALONE, restored.type());
        assertEquals("ORDER_9988_PAID", restored.correlationKey());
        assertEquals("Pagamento do pedido 9988", restored.displayName());
        assertNull(restored.coordinatorTaskId());
        assertNull(restored.pendingCorrelationKeys());
        assertNull(restored.matchPolicy());
    }

    @Test
    void roundTripsEventCatcherGroupParentAndChildFields() {
        ExternalTask parent = ExternalTask.builder()
                .id("parent-1")
                .taskDefinitionId("WAIT_ALL_PRODUCTS")
                .processInstanceId("proc-3")
                .processDefinitionId("def-1")
                .type(ExternalTaskType.EVENT_CATCHER_PARENT)
                .pendingCorrelationKeys(List.of("PROD_1_ACTIVATED", "PROD_2_ACTIVATED"))
                .matchPolicy(MatchPolicy.ALL)
                .build();

        ExternalTask child = ExternalTask.builder()
                .id("child-1")
                .taskDefinitionId("WAIT_ALL_PRODUCTS")
                .processInstanceId("proc-3")
                .processDefinitionId("def-1")
                .type(ExternalTaskType.EVENT_CATCHER_CHILD)
                .correlationKey("PROD_1_ACTIVATED")
                .displayName("Ativação: Produto 1")
                .matchPolicy(MatchPolicy.ALL)
                .coordinatorTaskId("parent-1")
                .build();

        ExternalTask restoredParent = ExternalTaskMapper.fromDocument(ExternalTaskMapper.toDocument(parent));
        ExternalTask restoredChild = ExternalTaskMapper.fromDocument(ExternalTaskMapper.toDocument(child));

        assertEquals(ExternalTaskType.EVENT_CATCHER_PARENT, restoredParent.type());
        assertEquals(List.of("PROD_1_ACTIVATED", "PROD_2_ACTIVATED"), restoredParent.pendingCorrelationKeys());
        assertEquals(MatchPolicy.ALL, restoredParent.matchPolicy());
        assertNull(restoredParent.coordinatorTaskId());

        assertEquals(ExternalTaskType.EVENT_CATCHER_CHILD, restoredChild.type());
        assertEquals("parent-1", restoredChild.coordinatorTaskId());
        assertEquals("PROD_1_ACTIVATED", restoredChild.correlationKey());
    }

    @Test
    void legacyDocumentWithoutNewFieldsMapsThemAsNull() {
        // Simula um documento persistido antes deste campo existir — sem migração.
        Document legacyDoc = new Document("_id", "task-legacy")
                .append("taskDefinitionId", "HUMAN_TASK")
                .append("processInstanceId", "proc-4")
                .append("processDefinitionId", "def-1");

        ExternalTask restored = ExternalTaskMapper.fromDocument(legacyDoc);

        assertNull(restored.type());
        assertNull(restored.correlationKey());
        assertNull(restored.coordinatorTaskId());
    }
}
