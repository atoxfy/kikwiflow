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

package io.kikwiflow.mcp;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Montagem enxuta de JSON Schema para {@code inputSchema} das ferramentas. */
public final class Schema {

    private Schema() {
    }

    /** @param properties pares nome → schema, na ordem em que devem aparecer para o modelo. */
    public static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }

    public static Map<String, Object> props(Object... nameSchemaPairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < nameSchemaPairs.length; i += 2) {
            properties.put((String) nameSchemaPairs[i], nameSchemaPairs[i + 1]);
        }
        return properties;
    }

    public static Map<String, Object> string(String description) {
        return typed("string", description);
    }

    public static Map<String, Object> enumOf(String description, Enum<?>[] values) {
        Map<String, Object> schema = typed("string", description);
        schema.put("enum", Arrays.stream(values).map(Enum::name).toList());
        return schema;
    }

    public static Map<String, Object> enumArray(String description, Enum<?>[] values) {
        Map<String, Object> schema = typed("array", description);
        schema.put("items", Map.of("type", "string", "enum", Arrays.stream(values).map(Enum::name).toList()));
        return schema;
    }

    public static Map<String, Object> dateTime(String description) {
        Map<String, Object> schema = typed("string", description);
        schema.put("format", "date-time");
        return schema;
    }

    public static Map<String, Object> integer(String description, int minimum, int maximum) {
        Map<String, Object> schema = typed("integer", description);
        schema.put("minimum", minimum);
        schema.put("maximum", maximum);
        return schema;
    }

    public static Map<String, Object> bool(String description) {
        return typed("boolean", description);
    }

    public static Map<String, Object> stringArray(String description) {
        Map<String, Object> schema = typed("array", description);
        schema.put("items", Map.of("type", "string"));
        return schema;
    }

    /** Lista de objetos com o schema {@code item}, de 1 a {@code maxItems}. */
    public static Map<String, Object> objectArray(String description, Map<String, Object> item, int maxItems) {
        Map<String, Object> schema = typed("array", description);
        schema.put("items", item);
        schema.put("minItems", 1);
        schema.put("maxItems", maxItems);
        return schema;
    }

    public static Map<String, Object> freeObject(String description) {
        Map<String, Object> schema = typed("object", description);
        schema.put("additionalProperties", true);
        return schema;
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }
}
