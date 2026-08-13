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
package io.kikwiflow.execution.evaluator;

import io.kikwiflow.execution.ProcessInstanceExecution;
import io.kikwiflow.execution.api.dto.CorrelationItem;
import io.kikwiflow.execution.api.resolver.CorrelationKeysProviderResolver;
import io.kikwiflow.model.definition.process.elements.CorrelationKeySource;
import io.kikwiflow.model.definition.process.elements.EventCatcherDefinition;
import io.kikwiflow.model.definition.process.elements.EventThrowerDefinition;
import io.kikwiflow.model.definition.process.elements.InterruptiveCatchEventDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateDefinition;
import io.kikwiflow.model.definition.process.policies.CorrelationTemplateSegment;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.CatchType;
import io.kikwiflow.model.execution.enumerated.TemplateSegmentType;
import io.kikwiflow.navigation.MapEvaluationContextAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolve a(s) chave(s) de correlação de qualquer {@link CorrelationKeySource} — hoje {@link EventCatcherDefinition}
 * (nó de fluxo principal, STANDALONE ou GROUP) e {@link InterruptiveCatchEventDefinition} (evento de borda,
 * sempre 1 única chave) — no mesmo estilo de {@link TimerDueDateEvaluator}: STATIC/VARIABLE resolvidos
 * diretamente da definição/variáveis, BEAN delegado a um {@link io.kikwiflow.execution.api.provider.CorrelationKeysProvider}
 * via {@link CorrelationKeysProviderResolver}.
 */
public class CorrelationKeyResolver {

    private final CorrelationKeysProviderResolver correlationKeysProviderResolver;

    public CorrelationKeyResolver(CorrelationKeysProviderResolver correlationKeysProviderResolver) {
        this.correlationKeysProviderResolver = correlationKeysProviderResolver;
    }

    /**
     * @param def {@code catchType: GROUP} permite resolver N chaves (scatter-gather); {@code STANDALONE} exige
     *            exatamente 1.
     */
    public List<CorrelationItem> resolve(EventCatcherDefinition def, ProcessInstanceExecution execution) {
        return resolveItems(def, def.catchType() == CatchType.GROUP, execution);
    }

    /**
     * Evento de borda: nunca há modo GROUP — sempre exatamente 1 chave, cuja chegada cancela o nó pai.
     */
    public List<CorrelationItem> resolve(InterruptiveCatchEventDefinition def, ProcessInstanceExecution execution) {
        return resolveItems(def, false, execution);
    }

    /**
     * Throw: sempre exatamente 1 chave — um EVENT_THROWER lança uma única mensagem para um único catcher
     * esperando por ela, mesma restrição de {@link InterruptiveCatchEventDefinition}.
     */
    public List<CorrelationItem> resolve(EventThrowerDefinition def, ProcessInstanceExecution execution) {
        return resolveItems(def, false, execution);
    }

    private List<CorrelationItem> resolveItems(CorrelationKeySource source, boolean allowMultipleKeys, ProcessInstanceExecution execution) {
        List<CorrelationItem> items = switch (source.providerType()) {
            case STATIC -> resolveStatic(source);
            case VARIABLE -> resolveFromVariable(source, allowMultipleKeys, execution);
            case BEAN -> resolveFromBean(source, execution);
            case TEMPLATE -> resolveFromTemplates(source, execution);
        };

        List<CorrelationItem> deduped = dedupPreservingOrder(items);

        if (deduped.isEmpty()) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' resolveu uma lista vazia de chaves de correlação.");
        }

        if (!allowMultipleKeys && deduped.size() > 1) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' deveria resolver exatamente 1 chave, mas o provider resolveu " + deduped.size() + ".");
        }

        return deduped;
    }

    private List<CorrelationItem> resolveStatic(CorrelationKeySource source) {
        if (source.staticKey() == null || source.staticKey().isBlank()) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' está configurado como STATIC, mas 'staticKey' é nulo ou vazio.");
        }
        String displayName = source.displayNamePrefix() != null || source.displayNameSuffix() != null
                ? nullToEmpty(source.displayNamePrefix()) + source.staticKey() + nullToEmpty(source.displayNameSuffix())
                : source.staticKey();
        return List.of(new CorrelationItem(source.staticKey(), displayName));
    }

    private List<CorrelationItem> resolveFromVariable(CorrelationKeySource source, boolean allowMultipleKeys, ProcessInstanceExecution execution) {
        if (source.providerVariable() == null || source.providerVariable().isBlank()) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' está configurado como VARIABLE, mas 'providerVariable' é nulo ou vazio.");
        }

        ProcessVariable variable = execution.getVariables().get(source.providerVariable());
        if (variable == null || variable.value() == null) {
            throw new IllegalStateException("Kikwiflow Engine: Variável de correlação não encontrada -> " + source.providerVariable());
        }

        if (allowMultipleKeys) {
            if (!(variable.value() instanceof List<?> rawList)) {
                throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                        "' permite múltiplas chaves com providerType VARIABLE, mas a variável '" + source.providerVariable() + "' não é uma lista.");
            }
            return rawList.stream().map(v -> toItem(source, String.valueOf(v))).toList();
        }

        return List.of(toItem(source, variable.value().toString()));
    }

    private List<CorrelationItem> resolveFromBean(CorrelationKeySource source, ProcessInstanceExecution execution) {
        if (source.providerBean() == null || source.providerBean().isBlank()) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' está configurado como BEAN, mas 'providerBean' é nulo ou vazio.");
        }

        return correlationKeysProviderResolver.getProvider(source.providerBean())
                .map(provider -> provider.resolveCorrelationItems(
                        new MapEvaluationContextAdapter(execution.getId(), execution.getVariables())))
                .orElseThrow(() -> new IllegalStateException(
                        "Execution Error: Nenhum CorrelationKeysProvider encontrado para o bean '" + source.providerBean() + "'."));
    }

    private List<CorrelationItem> resolveFromTemplates(CorrelationKeySource source, ProcessInstanceExecution execution) {
        if (source.correlationTemplates() == null || source.correlationTemplates().isEmpty()) {
            throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                    "' está configurado como TEMPLATE, mas 'correlationTemplates' está vazio.");
        }

        return source.correlationTemplates().stream()
                .map(template -> {
                    String key = renderSegments(source, template.keySegments(), execution);
                    String displayName = template.displayNameSegments() != null && !template.displayNameSegments().isEmpty()
                            ? renderSegments(source, template.displayNameSegments(), execution)
                            : key;
                    return new CorrelationItem(key, displayName);
                })
                .toList();
    }

    private String renderSegments(CorrelationKeySource source, List<CorrelationTemplateSegment> segments, ProcessInstanceExecution execution) {
        StringBuilder sb = new StringBuilder();
        for (CorrelationTemplateSegment segment : segments) {
            if (segment.type() == TemplateSegmentType.LITERAL) {
                sb.append(nullToEmpty(segment.value()));
            } else {
                ProcessVariable variable = execution.getVariables().get(segment.value());
                if (variable == null || variable.value() == null) {
                    throw new IllegalStateException("Kikwiflow Engine: nó de correlação '" + source.id() +
                            "' referencia a variável de template '" + segment.value() + "', que não foi encontrada.");
                }
                sb.append(variable.value());
            }
        }
        return sb.toString();
    }

    private CorrelationItem toItem(CorrelationKeySource source, String rawValue) {
        String key = nullToEmpty(source.keyPrefix()) + rawValue + nullToEmpty(source.keySuffix());
        String displayName = (source.displayNamePrefix() != null || source.displayNameSuffix() != null)
                ? nullToEmpty(source.displayNamePrefix()) + rawValue + nullToEmpty(source.displayNameSuffix())
                : key;
        return new CorrelationItem(key, displayName);
    }

    private List<CorrelationItem> dedupPreservingOrder(List<CorrelationItem> items) {
        Map<String, CorrelationItem> byKey = new LinkedHashMap<>();
        for (CorrelationItem item : items) {
            byKey.putIfAbsent(item.key(), item);
        }
        return List.copyOf(byKey.values());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
