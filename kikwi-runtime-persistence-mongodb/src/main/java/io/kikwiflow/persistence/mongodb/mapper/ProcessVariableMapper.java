/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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

import io.kikwiflow.model.execution.ProcessVariable;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;

public final class ProcessVariableMapper {

    private ProcessVariableMapper() {}

    public static Document toDocument(ProcessVariable variable) {
        if (variable == null) return null;
        return new Document("name", variable.name())
                .append("value", variable.value())
                .append("_class", variable.value() != null ? variable.value().getClass().getName() : null)
                .append("isTransient", variable.isTransient());
    }

    public static ProcessVariable fromDocumentToVariable(Document doc) {
        if (doc == null) return null;

        return new ProcessVariable(
                doc.getString("name"),
                doc.getBoolean("isTransient", false),
                readValue(doc)
        );
    }

    // "_class" is written on save but a plain doc.get("value") ignores it. The Mongo driver already
    // decoded "value" into whatever type its BsonTypeClassMap defaults to before this code ever runs
    // (e.g. a BSON date always decodes as java.util.Date, never Instant/LocalDate/LocalDateTime, and
    // Decimal128 never as BigDecimal), so Document.get("value", Class) can't help - it's a plain cast,
    // not a re-decode. "_class" is instead used to coerce that already-decoded value back to the
    // original Java type, the same way InstantMapper does for startedAt/endedAt.
    private static Object readValue(Document doc) {
        Object rawValue = doc.get("value");
        String className = doc.getString("_class");
        if (rawValue == null || className == null) {
            return rawValue;
        }
        try {
            return coerce(rawValue, Class.forName(className));
        } catch (ClassNotFoundException | IllegalArgumentException e) {
            return rawValue;
        }
    }

    private static Object coerce(Object rawValue, Class<?> targetType) {
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }
        if (rawValue instanceof Date date) {
            if (targetType == Instant.class) return date.toInstant();
            if (targetType == LocalDate.class) return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            if (targetType == LocalDateTime.class) return date.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
            if (targetType == LocalTime.class) return date.toInstant().atZone(ZoneOffset.UTC).toLocalTime();
        }
        if (rawValue instanceof Decimal128 decimal128 && Number.class.isAssignableFrom(targetType)) {
            return decimal128.bigDecimalValue();
        }
        if (rawValue instanceof String name && targetType.isEnum()) {
            return toEnum(targetType, name);
        }
        return rawValue;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toEnum(Class<?> enumType, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumType, name);
    }
}