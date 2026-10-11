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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitura tolerante dos argumentos de uma chamada. Modelos às vezes mandam número como string ou um valor único
 * onde se espera lista — aceitamos as duas formas e respondemos com {@link ToolException} legível quando não dá.
 */
public final class ToolArgs {

    private final Map<String, Object> raw;

    public ToolArgs(Map<String, Object> raw) {
        this.raw = raw != null ? raw : Map.of();
    }

    public String string(String name) {
        Object value = raw.get(name);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public String requiredString(String name) {
        String value = string(name);
        if (value == null) {
            throw ToolException.invalidArgument("Argumento obrigatório ausente: `" + name + "`.");
        }
        return value;
    }

    public int integer(String name, int defaultValue, int min, int max) {
        Object value = raw.get(name);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw ToolException.invalidArgument("`" + name + "` deve ser um inteiro, recebido: " + value);
        }
        if (parsed < min || parsed > max) {
            throw ToolException.invalidArgument("`" + name + "` deve estar entre " + min + " e " + max + ".");
        }
        return parsed;
    }

    public Integer optionalInteger(String name) {
        Object value = raw.get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return integer(name, 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public boolean bool(String name, boolean defaultValue) {
        Object value = raw.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    public BigDecimal decimal(String name) {
        Object value = raw.get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw ToolException.invalidArgument("`" + name + "` deve ser um número, recebido: " + value);
        }
    }

    public Instant instant(String name) {
        String value = string(name);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw ToolException.invalidArgument("`" + name + "` deve ser uma data-hora ISO-8601 em UTC (ex.: 2026-10-10T12:00:00Z), recebido: " + value);
        }
    }

    public List<String> strings(String name) {
        Object value = raw.get(name);
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString().trim());
                }
            }
        } else if (!value.toString().isBlank()) {
            result.add(value.toString().trim());
        }
        return result;
    }

    public <E extends Enum<E>> List<E> enums(String name, Class<E> type) {
        List<E> result = new ArrayList<>();
        for (String value : strings(name)) {
            try {
                result.add(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw ToolException.invalidArgument("Valor inválido para `" + name + "`: " + value
                        + ". Aceitos: " + List.of(type.getEnumConstants()));
            }
        }
        return result;
    }

    /** Lista de objetos JSON (cada um vira um {@link ToolArgs} próprio); ausente = lista vazia. */
    public List<ToolArgs> objects(String name) {
        Object value = raw.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw ToolException.invalidArgument("`" + name + "` deve ser uma lista de objetos JSON.");
        }
        List<ToolArgs> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                throw ToolException.invalidArgument("Cada item de `" + name + "` deve ser um objeto JSON.");
            }
            Map<String, Object> itemArgs = new LinkedHashMap<>();
            map.forEach((k, v) -> itemArgs.put(String.valueOf(k), v));
            result.add(new ToolArgs(itemArgs));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> object(String name) {
        Object value = raw.get(name);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        throw ToolException.invalidArgument("`" + name + "` deve ser um objeto JSON (chave → valor).");
    }
}
