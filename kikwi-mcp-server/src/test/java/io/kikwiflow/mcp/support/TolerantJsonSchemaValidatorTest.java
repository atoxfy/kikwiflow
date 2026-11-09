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
package io.kikwiflow.mcp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TolerantJsonSchemaValidator — conflito de json-schema-validator no host")
class TolerantJsonSchemaValidatorTest {

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("id", Map.of("type", "string")),
            "required", java.util.List.of("id"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("Dado o validador padrão do SDK compatível com o classpath")
    class Compatible {

        private final TolerantJsonSchemaValidator validator = new TolerantJsonSchemaValidator(mapper);

        @Test
        @DisplayName("Então valida schema e conteúdo de verdade")
        void delegatesToTheSdk() {
            assertTrue(validator.validateSchema(SCHEMA).valid());
            assertTrue(validator.validate(SCHEMA, Map.of("id", "x")).valid());
            assertFalse(validator.validate(SCHEMA, Map.of()).valid());
            assertFalse(validator.isDisabled());
        }
    }

    @Nested
    @DisplayName("Dado um json-schema-validator incompatível no host")
    class Incompatible {

        private final AtomicInteger calls = new AtomicInteger();
        private final TolerantJsonSchemaValidator validator = new TolerantJsonSchemaValidator(mapper, () -> {
            calls.incrementAndGet();
            return new JsonSchemaValidator() {
                @Override
                public ValidationResponse validate(Map<String, Object> schema, Object content) {
                    throw new NoSuchMethodError("com.networknt.schema.Schema.validate");
                }

                @Override
                public ValidationResponse validateSchema(Map<String, Object> schema) {
                    throw new NoSuchMethodError("com.networknt.schema.Schema.validate");
                }
            };
        });

        @Test
        @DisplayName("Quando o SDK valida um schema de tool, Então não derruba o startup e desliga a validação")
        void degradesInsteadOfFailing() {
            assertTrue(validator.validateSchema(SCHEMA).valid());
            assertTrue(validator.isDisabled());

            JsonSchemaValidator.ValidationResponse response = validator.validate(SCHEMA, Map.of("id", "x"));
            assertTrue(response.valid());
            assertEquals("{\"id\":\"x\"}", response.jsonStructuredOutput());
            assertEquals(1, calls.get());
        }
    }
}
