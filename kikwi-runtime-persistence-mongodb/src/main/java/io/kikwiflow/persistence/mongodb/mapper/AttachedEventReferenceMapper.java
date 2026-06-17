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

import io.kikwiflow.model.execution.node.AttachedEventReference;
import org.bson.Document;

public final class AttachedEventReferenceMapper {

    private AttachedEventReferenceMapper() {}

    public static Document toDocument(AttachedEventReference reference) {
        if (reference == null) return null;

        return new Document("instanceId", reference.instanceId())
                .append("definitionId", reference.definitionId());
    }

    public static AttachedEventReference fromDocument(Document doc) {
        if (doc == null) return null;

        return new AttachedEventReference(
                doc.getString("instanceId"),
                doc.getString("definitionId")
        );
    }
}