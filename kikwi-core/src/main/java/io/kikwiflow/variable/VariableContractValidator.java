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
package io.kikwiflow.variable;

import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;
import io.kikwiflow.model.execution.ProcessVariable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Valida um conjunto de {@link VariableBinding} contra o catálogo do processo e o estado de variáveis
 * informado. Sem estado além do cache de {@link Pattern}s, sem reflection, sem Spring — o mesmo validador
 * serve aos {@code inputs} (hoje) e aos {@code outputs} (fase 2, ver docs/engine/26).
 * <p>
 * Regras, por vínculo:
 * <ul>
 *   <li>{@code required} e valor ausente/vazio (null, string em branco, coleção vazia) → {@code REQUIRED};</li>
 *   <li>valor presente é checado contra o {@code format} — aceitando tanto tipos Java quanto o que chega de
 *   JSON (números como Integer/Double, datas como string ISO) e do Mongo ({@code java.util.Date});</li>
 *   <li>{@code validationRegex} (só formatos de texto) precisa casar com o valor inteiro.</li>
 * </ul>
 */
public class VariableContractValidator {

    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public List<VariableValidationError> validate(List<VariableBinding> bindings,
                                                  ProcessDefinition processDefinition,
                                                  Map<String, ProcessVariable> variables) {
        List<VariableValidationError> errors = new ArrayList<>();
        if (bindings == null || bindings.isEmpty()) {
            return errors;
        }

        for (VariableBinding binding : bindings) {
            String key = binding.variable();
            VariableDeclaration declaration = processDefinition.findVariableDeclaration(key).orElse(null);
            if (declaration == null) {
                errors.add(new VariableValidationError(key, VariableValidationError.UNDECLARED,
                        "Variable '" + key + "' is not declared in the process variable catalog."));
                continue;
            }

            ProcessVariable variable = variables != null ? variables.get(key) : null;
            Object value = variable != null ? variable.value() : null;

            if (isEmpty(value)) {
                if (binding.requiresValue()) {
                    errors.add(new VariableValidationError(key, VariableValidationError.REQUIRED,
                            "Variable '" + declaration.label() + "' is required."));
                }
                continue;
            }

            VariableValidationError formatError = checkFormat(declaration, value);
            if (formatError != null) {
                errors.add(formatError);
            }
        }
        return errors;
    }

    /** Atalho para os hooks do motor: lança {@link VariableValidationException} se houver qualquer erro. */
    public void validateOrThrow(String nodeId, List<VariableBinding> bindings, ProcessDefinition processDefinition,
                                Map<String, ProcessVariable> variables) {
        List<VariableValidationError> errors = validate(bindings, processDefinition, variables);
        if (!errors.isEmpty()) {
            throw new VariableValidationException(nodeId, errors);
        }
    }

    private VariableValidationError checkFormat(VariableDeclaration declaration, Object value) {
        String key = declaration.key();
        return switch (declaration.format()) {
            case SHORT_TEXT, LONG_TEXT -> {
                if (!(value instanceof CharSequence text)) {
                    yield invalidFormat(key, "text");
                }
                String regex = declaration.validationRegex();
                if (regex != null && !regex.isBlank()
                        && !patternCache.computeIfAbsent(regex, Pattern::compile).matcher(text).matches()) {
                    String message = declaration.validationMessage() != null
                            ? declaration.validationMessage()
                            : "Variable '" + declaration.label() + "' does not match the expected pattern.";
                    yield new VariableValidationError(key, VariableValidationError.PATTERN_MISMATCH, message);
                }
                yield null;
            }
            case INTEGER -> isInteger(value) ? null : invalidFormat(key, "an integer number");
            case DECIMAL, MONEY -> {
                // Com kikwi:numberFormat declarado, texto só vale no formato do catálogo (a mesma leitura estrita da
                // agregação: o que passa aqui é o que soma lá). Sem ele, a gramática do BigDecimal de sempre.
                VariableNumberFormat numberFormat = numberFormatOf(declaration);
                if (numberFormat == null) {
                    yield isDecimal(value) ? null : invalidFormat(key, "a decimal number");
                }
                yield numberFormat.toDecimal(value) != null ? null
                        : invalidFormat(key, "a decimal number written as " + example(numberFormat));
            }
            case BOOLEAN -> value instanceof Boolean ? null : invalidFormat(key, "a boolean");
            case DATE -> isDate(value) ? null : invalidFormat(key, "a date (yyyy-MM-dd)");
            case DATETIME -> isDateTime(value) ? null : invalidFormat(key, "an ISO-8601 date-time with offset");
            case SINGLE_SELECT -> {
                if (!(value instanceof String selected)) {
                    yield invalidFormat(key, "a single option value");
                }
                yield optionValues(declaration).contains(selected) ? null : invalidOption(key, selected);
            }
            case MULTI_SELECT -> {
                if (!(value instanceof Collection<?> selected)) {
                    yield invalidFormat(key, "a list of option values");
                }
                Set<String> allowed = optionValues(declaration);
                for (Object item : selected) {
                    if (!(item instanceof String s) || !allowed.contains(s)) {
                        yield invalidOption(key, item);
                    }
                }
                yield null;
            }
            case FILE -> checkFile(declaration, value);
        };
    }

    /**
     * FILE (docs/engine/31, E4): referência {@code {uri, name, mimeType, size}}, ou lista delas com
     * {@code kikwi:file.multiple}. Bytes nunca entram em variável; o esquema da {@code uri} é opaco.
     */
    private static VariableValidationError checkFile(VariableDeclaration declaration, Object value) {
        String key = declaration.key();
        Map<String, String> properties = declaration.extensionProperties() != null ? declaration.extensionProperties() : Map.of();
        boolean multiple = Boolean.parseBoolean(properties.get(VariableFormat.FILE_MULTIPLE));
        List<String> mimeTypes = parseMimeTypes(properties.get(VariableFormat.FILE_MIME_TYPES));

        List<?> files;
        if (multiple) {
            if (!(value instanceof Collection<?> collection)) {
                return invalidFormat(key, "a list of file references {uri, mimeType}");
            }
            files = new ArrayList<>(collection);
            Integer maxCount = parsePositiveInt(properties.get(VariableFormat.FILE_MAX_COUNT));
            if (maxCount != null && files.size() > maxCount) {
                return new VariableValidationError(key, VariableValidationError.TOO_MANY_FILES,
                        "Variable '" + declaration.label() + "' accepts at most " + maxCount + " files.");
            }
        } else {
            files = List.of(value);
        }

        for (Object file : files) {
            if (!(file instanceof Map<?, ?> reference) || isBlankString(reference.get("uri"))
                    || isBlankString(reference.get("mimeType"))) {
                return invalidFormat(key, multiple ? "a list of file references {uri, mimeType}"
                        : "a file reference {uri, mimeType}");
            }
            String mimeType = reference.get("mimeType").toString();
            if (!mimeTypes.isEmpty() && mimeTypes.stream().noneMatch(accepted -> mimeMatches(accepted, mimeType))) {
                return new VariableValidationError(key, VariableValidationError.INVALID_FILE_TYPE,
                        "Variable '" + declaration.label() + "' does not accept files of type '" + mimeType + "'.");
            }
        }
        return null;
    }

    /** {@code image/*} casa com qualquer {@code image/...}; o resto é igualdade sem caixa. */
    static boolean mimeMatches(String accepted, String mimeType) {
        String a = accepted.toLowerCase(java.util.Locale.ROOT);
        String m = mimeType.trim().toLowerCase(java.util.Locale.ROOT);
        return a.endsWith("/*") ? m.startsWith(a.substring(0, a.length() - 1)) : a.equals(m);
    }

    static List<String> parseMimeTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlankString(Object value) {
        return !(value instanceof CharSequence text) || text.toString().isBlank();
    }

    private static VariableNumberFormat numberFormatOf(VariableDeclaration declaration) {
        Map<String, String> properties = declaration.extensionProperties();
        return properties == null ? null : VariableNumberFormat.fromCatalog(properties.get(VariableNumberFormat.CATALOG_PROPERTY));
    }

    private static String example(VariableNumberFormat format) {
        return switch (format) {
            case PLAIN -> "45000.00";
            case PT_BR -> "45.000,00";
            case AUTO -> "45000.00 or 45.000,00";
        };
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || (value instanceof CharSequence cs && cs.toString().isBlank())
                || (value instanceof Collection<?> c && c.isEmpty());
    }

    private static boolean isInteger(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            return true;
        }
        if (value instanceof BigDecimal bd) {
            return bd.signum() == 0 || bd.stripTrailingZeros().scale() <= 0;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) && d == Math.rint(d);
        }
        if (value instanceof String s) {
            try {
                new BigInteger(s.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isDecimal(Object value) {
        if (value instanceof Double || value instanceof Float) {
            return Double.isFinite(((Number) value).doubleValue());
        }
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String s) {
            try {
                new BigDecimal(s.trim());
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isDate(Object value) {
        if (value instanceof LocalDate || value instanceof Date) {
            return true;
        }
        if (value instanceof String s) {
            try {
                LocalDate.parse(s.trim());
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return false;
    }

    private static boolean isDateTime(Object value) {
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime || value instanceof Instant
                || value instanceof LocalDateTime || value instanceof Date) {
            return true;
        }
        if (value instanceof String s) {
            try {
                OffsetDateTime.parse(s.trim());
                return true;
            } catch (DateTimeParseException e) {
                return false;
            }
        }
        return false;
    }

    private static Set<String> optionValues(VariableDeclaration declaration) {
        return declaration.options().stream().map(VariableOption::value).collect(Collectors.toSet());
    }

    private static VariableValidationError invalidFormat(String key, String expected) {
        return new VariableValidationError(key, VariableValidationError.INVALID_FORMAT,
                "Variable '" + key + "' must be " + expected + ".");
    }

    private static VariableValidationError invalidOption(String key, Object value) {
        return new VariableValidationError(key, VariableValidationError.INVALID_OPTION,
                "Value '" + value + "' is not an allowed option for variable '" + key + "'.");
    }
}
