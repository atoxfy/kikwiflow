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

public class SchedulePolicyMapper {

    public static SchedulePolicy mapToDefinition(Document document){

        Document schedulePolicyDoc = document.get("schedulePolicy", Document.class);
        SchedulePolicy schedulePolicy = null;
        if(schedulePolicyDoc != null){
            schedulePolicy = new SchedulePolicy(
                    ScheduleType.valueOf(schedulePolicyDoc.getString("type")),
                    schedulePolicyDoc.getString("expression"),
                    schedulePolicyDoc.getList("fixedDates", String.class),
                    schedulePolicyDoc.getInteger("maxOccurrences")
            );
        }

        return schedulePolicy;
    }

    public static void mapToDocument(Document doc, SchedulePolicy schedulePolicy){
        if(schedulePolicy != null){
            Document schedulePolicyDoc = new Document("expression", schedulePolicy.expression())
                    .append("type", schedulePolicy.type().name())
                    .append("fixedDates", schedulePolicy.fixedDates())
                    .append("maxOccurrences", schedulePolicy.maxOccurrences());

            doc.append("schedulePolicy", schedulePolicyDoc);
        }
    }
}
