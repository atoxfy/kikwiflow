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

import com.mongodb.MongoClientSettings;
import io.kikwiflow.model.execution.ProcessVariable;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests push documents through a real BSON binary encode/decode (not just plain in-memory
 * {@link Document} manipulation), because that's the only way the {@code Instant -> java.util.Date}
 * degradation this class works around actually reproduces: {@code Document.get(key, Class)} is a
 * plain unchecked cast, so any test that never leaves the JVM's object graph would pass even
 * without the fix.
 */
class ProcessVariableMapperTest {

    private static final DocumentCodec CODEC = new DocumentCodec(MongoClientSettings.getDefaultCodecRegistry());

    private static Document bsonRoundTrip(Document original) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        CODEC.encode(writer, original, EncoderContext.builder().build());
        writer.close();

        BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(buffer.toByteArray()));
        Document decoded = CODEC.decode(reader, DecoderContext.builder().build());
        reader.close();
        return decoded;
    }

    private static ProcessVariable roundTrip(ProcessVariable variable) {
        Document doc = bsonRoundTrip(ProcessVariableMapper.toDocument(variable));
        return ProcessVariableMapper.fromDocumentToVariable(doc);
    }

    @Test
    void roundTripsInstantValueWithoutDegradingToDate() {
        Instant horarioSorteio = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ProcessVariable restored = roundTrip(new ProcessVariable("horarioSorteio", horarioSorteio));

        assertInstanceOf(Instant.class, restored.value());
        assertEquals(horarioSorteio, restored.value());
    }

    @Test
    void roundTripsLocalDateValue() {
        LocalDate birthDate = LocalDate.of(1990, 5, 20);

        ProcessVariable restored = roundTrip(new ProcessVariable("birthDate", birthDate));

        assertInstanceOf(LocalDate.class, restored.value());
        assertEquals(birthDate, restored.value());
    }

    @Test
    void roundTripsBigDecimalValue() {
        ProcessVariable restored = roundTrip(new ProcessVariable("premium", new BigDecimal("1234.56")));

        assertInstanceOf(BigDecimal.class, restored.value());
        assertEquals(new BigDecimal("1234.56"), restored.value());
    }

    @Test
    void roundTripsSimpleValues() {
        ProcessVariable restored = roundTrip(new ProcessVariable("riskScore", 87.5));

        assertEquals("riskScore", restored.name());
        assertEquals(87.5, restored.value());
        assertFalse(restored.isTransient());
    }

    @Test
    void roundTripsIsTransientFlag() {
        ProcessVariable restored = roundTrip(new ProcessVariable("scratch", true, "temp"));

        assertTrue(restored.isTransient());
    }

    @Test
    void fallsBackToRawValueWhenClassHintIsMissing() {
        Document doc = bsonRoundTrip(new Document("name", "legacy").append("value", "aprovado"));

        ProcessVariable restored = ProcessVariableMapper.fromDocumentToVariable(doc);

        assertEquals("aprovado", restored.value());
    }

    @Test
    void fallsBackToRawValueWhenClassHintIsUnresolvable() {
        Document doc = bsonRoundTrip(new Document("name", "legacy")
                .append("value", "aprovado")
                .append("_class", "com.example.NotOnClasspath"));

        ProcessVariable restored = ProcessVariableMapper.fromDocumentToVariable(doc);

        assertEquals("aprovado", restored.value());
    }

    @Test
    void toDocumentHandlesNullValue() {
        ProcessVariable restored = roundTrip(new ProcessVariable("empty", null));

        assertNull(restored.value());
    }
}
