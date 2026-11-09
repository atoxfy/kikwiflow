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

import io.kikwiflow.model.event.AgentActivityRecorded;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.util.MongoKeyEncoder;
import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper dos quatro eventos {@code AGENT_*}, que compartilham {@link AgentActivityRecorded}. As chaves de
 * {@code data} são escapadas em qualquer profundidade (vêm do runtime e podem ter ponto) e os subdocumentos voltam
 * como mapas simples.
 */
public final class AgentActivityRecordedMapper {

    private AgentActivityRecordedMapper() {}

    public static Document toDocument(AgentActivityRecorded event) {
        return new Document("externalTaskId", event.externalTaskId())
                .append("processDefinitionId", event.processDefinitionId())
                .append("processInstanceId", event.processInstanceId())
                .append("tenantId", event.tenantId())
                .append("taskDefinitionId", event.taskDefinitionId())
                .append("workerId", event.workerId())
                .append("at", event.at() != null ? java.util.Date.from(event.at()) : null)
                .append("data", encode(event.data()));
    }

    public static AgentActivityRecorded fromDocument(Document doc) {
        Document data = doc.get("data", Document.class);
        return new AgentActivityRecorded(
                doc.getString("externalTaskId"),
                doc.getString("processDefinitionId"),
                doc.getString("processInstanceId"),
                doc.getString("tenantId"),
                doc.getString("taskDefinitionId"),
                doc.getString("workerId"),
                InstantMapper.mapToInstant("at", doc),
                data != null ? decode(data) : Map.of()
        );
    }

    private static Document encode(Map<?, ?> map) {
        Document doc = new Document();
        map.forEach((key, value) -> doc.put(MongoKeyEncoder.encode(String.valueOf(key)), encodeValue(value)));
        return doc;
    }

    private static Object encodeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return encode(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AgentActivityRecordedMapper::encodeValue).toList();
        }
        return value;
    }

    private static Map<String, Object> decode(Map<String, Object> doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        doc.forEach((key, value) -> map.put(MongoKeyEncoder.decode(key), decodeValue(value)));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Object decodeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return decode((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AgentActivityRecordedMapper::decodeValue).toList();
        }
        return value;
    }
}
