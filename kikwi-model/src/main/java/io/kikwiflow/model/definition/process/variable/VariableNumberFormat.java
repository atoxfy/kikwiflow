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

package io.kikwiflow.model.definition.process.variable;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Como um número gravado como <b>texto</b> numa variável deve ser lido. Valores numéricos nativos (int, long, double,
 * decimal) valem em qualquer formato. A leitura é estrita: texto que não casa com o padrão do formato fica de fora
 * (conta como não numérico) — {@code "45.000"} é 45 mil em pt-BR e 45 em notação simples, então quem escolhe é quem
 * conhece o dado: o catálogo ({@link #CATALOG_PROPERTY}) ou quem pergunta.
 */
public enum VariableNumberFormat {

    /** {@code 45000.00}, {@code -12.5}, {@code 7} — o que {@code BigDecimal.toPlainString()} e {@code toFixed(2)} produzem. */
    PLAIN,

    /**
     * {@code 45.000,00}, {@code 45000,00}, {@code R$ 1.234,5}, {@code 7} — o que {@code toLocaleString('pt-BR')} produz.
     * Separador de milhar só em grupos de 3; {@code R$} e espaços (inclusive não separáveis) são removidos antes.
     */
    PT_BR,

    /**
     * Para variáveis gravadas ora num formato, ora no outro: tenta PLAIN e, se não casar, PT_BR. Ambíguo de propósito
     * e documentado: {@code "45.000"} casa com PLAIN e vira 45. Use só quando o próprio sistema que grava lê assim.
     */
    AUTO;

    /** Propriedade de {@code VariableDeclaration.extensionProperties} com o formato do texto gravado. */
    public static final String CATALOG_PROPERTY = "kikwi:numberFormat";

    /** Padrão de PLAIN, aplicado ao texto aparado. Também usado pelas implementações em banco. */
    public static final String PLAIN_REGEX = "^-?[0-9]+(\\.[0-9]+)?$";

    /** Padrão de PT_BR, aplicado ao texto sem {@code R$} nem espaços. Também usado pelas implementações em banco. */
    public static final String PT_BR_REGEX = "^-?([0-9]{1,3}(\\.[0-9]{3})+|[0-9]+)(,[0-9]+)?$";

    private static final Pattern PLAIN_PATTERN = Pattern.compile(PLAIN_REGEX);
    private static final Pattern PT_BR_PATTERN = Pattern.compile(PT_BR_REGEX);

    /** Limpeza de PLAIN: apara espaços nas pontas (inclusive não separáveis). */
    public static String cleanPlain(String text) {
        return text.replace(' ', ' ').trim();
    }

    /** Limpeza de PT_BR: remove {@code R$} e todo espaço, para aceitar {@code "R$ 45.000,00"} e {@code "-R$ 10,00"}. */
    public static String cleanPtBr(String text) {
        return text.replace("R$", "").replace(" ", "").replace(" ", "");
    }

    /** Texto → número, ou {@code null} se não estiver neste formato. */
    public BigDecimal parse(String text) {
        return switch (this) {
            case PLAIN -> parsePlain(text);
            case PT_BR -> parsePtBr(text);
            case AUTO -> {
                BigDecimal plain = parsePlain(text);
                yield plain != null ? plain : parsePtBr(text);
            }
        };
    }

    /** Valor de variável → número: nativos sempre; texto conforme o formato; o resto, {@code null}. */
    public BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return value instanceof String text ? parse(text) : null;
    }

    /** Formato declarado no catálogo ({@code "PT_BR"}, {@code "pt-br"}...), ou {@code null} se ausente/inválido. */
    public static VariableNumberFormat fromCatalog(String propertyValue) {
        if (propertyValue == null) {
            return null;
        }
        try {
            return valueOf(propertyValue.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal parsePlain(String text) {
        String cleaned = cleanPlain(text);
        return PLAIN_PATTERN.matcher(cleaned).matches() ? new BigDecimal(cleaned) : null;
    }

    private static BigDecimal parsePtBr(String text) {
        String cleaned = cleanPtBr(text);
        return PT_BR_PATTERN.matcher(cleaned).matches()
                ? new BigDecimal(cleaned.replace(".", "").replace(',', '.'))
                : null;
    }
}
