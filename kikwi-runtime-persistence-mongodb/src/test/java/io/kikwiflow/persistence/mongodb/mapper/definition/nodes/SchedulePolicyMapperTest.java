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

package io.kikwiflow.persistence.mongodb.mapper.definition.nodes;

import io.kikwiflow.model.definition.process.policies.SchedulePolicy;
import io.kikwiflow.model.execution.enumerated.ScheduleType;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchedulePolicyMapperTest {

    @Test
    void roundTripsMaxOccurrences() {
        SchedulePolicy original = new SchedulePolicy(ScheduleType.RATE_DURATION, "PT1H", null, 3);

        Document doc = new Document();
        SchedulePolicyMapper.mapToDocument(doc, original);
        SchedulePolicy restored = SchedulePolicyMapper.mapToDefinition(doc);

        assertEquals(ScheduleType.RATE_DURATION, restored.type());
        assertEquals("PT1H", restored.expression());
        assertEquals(3, restored.maxOccurrences());
    }

    @Test
    void roundTripsNullMaxOccurrencesAsUnboundedRecurrence() {
        SchedulePolicy original = new SchedulePolicy(ScheduleType.FIXED_DATES, null, List.of("2026-12-31T23:59:59Z"), null);

        Document doc = new Document();
        SchedulePolicyMapper.mapToDocument(doc, original);
        SchedulePolicy restored = SchedulePolicyMapper.mapToDefinition(doc);

        assertNull(restored.maxOccurrences());
        assertEquals(List.of("2026-12-31T23:59:59Z"), restored.fixedDates());
    }
}
