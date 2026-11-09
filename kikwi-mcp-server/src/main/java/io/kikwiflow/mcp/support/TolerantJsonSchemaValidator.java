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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link JsonSchemaValidator} do SDK MCP que não derruba o host por conflito de classpath.
 * <p>
 * O {@code DefaultJsonSchemaValidator} do {@code mcp-json-jackson2} é compilado contra uma versão específica de
 * {@code com.networknt:json-schema-validator} (2.0.x no SDK 2.0.1). Se o host fixar outra (ex.: 3.x, cuja API
 * mudou), o SDK falha com {@link NoSuchMethodError} já no registro das tools e a aplicação inteira não sobe —
 * mesmo com o MCP sendo só um acessório. Aqui um {@link LinkageError} desliga a validação de schema do MCP
 * (as tools continuam validando os próprios argumentos) e registra um aviso único.
 */
public final class TolerantJsonSchemaValidator implements JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(TolerantJsonSchemaValidator.class);

    private final ObjectMapper mapper;
    private final Supplier<JsonSchemaValidator> delegateFactory;
    private volatile JsonSchemaValidator delegate;
    private volatile boolean disabled;

    public TolerantJsonSchemaValidator(ObjectMapper mapper) {
        this(mapper, () -> new DefaultJsonSchemaValidator(mapper));
    }

    TolerantJsonSchemaValidator(ObjectMapper mapper, Supplier<JsonSchemaValidator> delegateFactory) {
        this.mapper = mapper;
        this.delegateFactory = delegateFactory;
    }

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        if (!disabled) {
            try {
                return delegate().validate(schema, structuredContent);
            } catch (LinkageError e) {
                disable(e);
            }
        }
        try {
            return ValidationResponse.asValid(mapper.writeValueAsString(structuredContent));
        } catch (JsonProcessingException e) {
            return ValidationResponse.asInvalid("Falha ao serializar o conteúdo estruturado: " + e.getMessage());
        }
    }

    @Override
    public ValidationResponse validateSchema(Map<String, Object> schema) {
        if (!disabled) {
            try {
                return delegate().validateSchema(schema);
            } catch (LinkageError e) {
                disable(e);
            }
        }
        return ValidationResponse.asValid(null);
    }

    public boolean isDisabled() {
        return disabled;
    }

    private JsonSchemaValidator delegate() {
        JsonSchemaValidator current = delegate;
        if (current == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = delegateFactory.get();
                }
                current = delegate;
            }
        }
        return current;
    }

    private synchronized void disable(LinkageError e) {
        if (disabled) {
            return;
        }
        disabled = true;
        log.warn("Kikwiflow MCP: validação de JSON Schema desligada — a versão de com.networknt:json-schema-validator "
                + "no classpath é incompatível com o SDK MCP ({}). Alinhe a dependência à versão esperada pelo "
                + "mcp-json-jackson2 para reativá-la.", e.toString());
    }
}
