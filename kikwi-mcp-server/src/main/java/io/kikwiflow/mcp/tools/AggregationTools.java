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

package io.kikwiflow.mcp.tools;

import io.kikwiflow.management.dtos.ProcessInstanceSearchRequest;
import io.kikwiflow.management.mapper.ProcessInstanceQueryMapper;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.TenantGuard;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.query.VariableAggregation;
import io.kikwiflow.persistence.api.query.VariableAggregationResult;
import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;
import io.kikwiflow.persistence.api.query.VariableProfile;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.kikwiflow.mcp.Schema.dateTime;
import static io.kikwiflow.mcp.Schema.enumArray;
import static io.kikwiflow.mcp.Schema.enumOf;
import static io.kikwiflow.mcp.Schema.freeObject;
import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.objectArray;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.Schema.stringArray;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/**
 * Perguntas de "quanto" sobre os dados das instâncias, em duas ferramentas que seguem o roteiro "definição primeiro,
 * dados depois": {@code profile_process_variables} mostra o que as instâncias gravam de fato (chaves, tipos, formato
 * numérico) lado a lado com o catálogo; {@code aggregate_process_variables} soma/mede. As contas rodam no banco
 * ({@code ProcessInstanceQuery.aggregate}/{@code profileVariables}); só acumulados saem, nunca valores individuais, e
 * só de variáveis que a identidade pode ler.
 */
public class AggregationTools {

    static final int MAX_GROUPS = 50;
    static final int DEFAULT_SAMPLE = 1000;

    private final QueryRepository queryRepository;
    private final VariableSecurityPolicyManager securityPolicyManager;
    private final DefinitionLookup definitions;
    private final MonitorLinks links;

    public AggregationTools(QueryRepository queryRepository, VariableSecurityPolicyManager securityPolicyManager,
                            MonitorLinks links) {
        this.queryRepository = queryRepository;
        this.securityPolicyManager = securityPolicyManager;
        this.definitions = new DefinitionLookup(queryRepository);
        this.links = links;
    }

    public List<KikwiTool> tools() {
        Map<String, Object> metric = object(props(
                "variable", string("Variável numérica a somar (chave exata, ex.: `compra.valorNegocio`)."),
                "minus", string("Opcional: variável subtraída de `variable` em cada instância que tem as duas "
                        + "(ex.: spread = `venda.valor` minus `compra.valorNegocio`)."),
                "label", string("Opcional: nome da métrica no resultado (ex.: \"spread\").")), "variable");

        return List.of(
                KikwiTool.read("profile_process_variables", "Perfil das variáveis gravadas",
                        "Mostra quais variáveis as instâncias de um processo gravam DE FATO (amostra das mais recentes): "
                                + "em quantas aparecem, com que tipo, se leem como número e em que formato (PLAIN "
                                + "\"45000.00\" ou PT_BR \"45.000,00\"), lado a lado com o catálogo (label, descrição, "
                                + "formato). Use antes de aggregate_process_variables quando a variável não estiver no "
                                + "catálogo de describe_process ou para escolher `numberFormat`. Também aponta variáveis "
                                + "gravadas e não declaradas no catálogo. Só estatística: nenhum valor é devolvido.",
                        object(props(
                                "processDefinitionKey", string("Chave do processo (todas as versões)."),
                                "activeNodeId", string("Só instâncias com token ativo neste nó."),
                                "statuses", enumArray("Status das instâncias (padrão: todos).", ProcessInstanceStatus.values()),
                                "sampleSize", integer("Instâncias mais recentes examinadas (padrão " + DEFAULT_SAMPLE + ").",
                                        1, VariableProfile.MAX_SAMPLE)),
                                "processDefinitionKey"),
                        this::profile),
                KikwiTool.read("aggregate_process_variables", "Somar variáveis de instâncias",
                        "Responde perguntas de \"quanto\" sobre instâncias de um processo: soma, média, mínimo e máximo "
                                + "de variáveis numéricas, opcionalmente agrupado por etapa (ACTIVE_NODE) ou pelo valor de "
                                + "outra variável (VARIABLE). Ex.: \"quanto dinheiro está parado em Em estoque\" = metrics "
                                + "[{variable: valorCompra}] com activeNodeId do nó; spread = {variable: valorVenda, minus: "
                                + "valorCompra}. Roteiro: describe_process (catálogo: significado, moeda, numberFormat) → "
                                + "profile_process_variables se a chave ou o formato não estiverem claros → esta. Filtros "
                                + "iguais aos de search_process_instances; por padrão só instâncias ACTIVE. Números gravados "
                                + "como texto são lidos, de forma estrita, no formato que o catálogo declara para cada "
                                + "variável; para as não declaradas vale `numberFormat` (padrão PLAIN). O resultado traz o "
                                + "formato usado por variável e quantas instâncias não tinham a variável (`missing`) ou "
                                + "tinham valor fora do formato (`nonNumeric`) — mencione isso na resposta.",
                        object(props(
                                "processDefinitionKey", string("Chave do processo (todas as versões)."),
                                "metrics", objectArray("O que medir (1 a " + VariableAggregation.MAX_METRICS + ").", metric,
                                        VariableAggregation.MAX_METRICS),
                                "numberFormat", enumOf("Formato das variáveis SEM formato no catálogo: PLAIN (\"45000.00\"), "
                                        + "PT_BR (\"45.000,00\") ou AUTO (tenta PLAIN, depois PT_BR; \"45.000\" vira 45). "
                                        + "Padrão PLAIN. O formato do catálogo sempre vence — se estiver errado, corrija o "
                                        + "catálogo.", VariableNumberFormat.values()),
                                "groupBy", enumOf("Agrupamento (padrão NONE = só totais). ACTIVE_NODE: por etapa onde o "
                                        + "token está; VARIABLE: pelo valor de `groupByVariable`.", VariableAggregation.GroupBy.values()),
                                "groupByVariable", string("Variável que define os grupos, com groupBy=VARIABLE (ex.: `responsavel.nome`)."),
                                "maxGroups", integer("Máximo de grupos (os maiores por nº de instâncias; padrão 20).", 1, MAX_GROUPS),
                                "activeNodeId", string("Só instâncias com token ativo neste nó (id do nó, ver describe_process)."),
                                "statuses", enumArray("Status das instâncias (padrão: só ACTIVE).", ProcessInstanceStatus.values()),
                                "startedAfter", dateTime("Iniciadas a partir de (ISO-8601 UTC)."),
                                "startedBefore", dateTime("Iniciadas antes de (ISO-8601 UTC)."),
                                "variables", freeObject("Igualdade exata de variáveis, ex.: {\"canal\": \"APP\"}."),
                                "variablesExist", stringArray("Só instâncias que possuem estas variáveis.")),
                                "processDefinitionKey", "metrics"),
                        this::aggregate));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // profile_process_variables
    // ---------------------------------------------------------------------------------------------------------------

    private ToolResult profile(ToolArgs args, McpCallContext context) {
        String key = args.requiredString("processDefinitionKey");
        List<ProcessDefinition> versions = definitions.versionsOf(key);
        ProcessDefinition latest = versions.get(0);
        String activeNodeId = args.string("activeNodeId");
        List<ProcessInstanceStatus> statuses = args.enums("statuses", ProcessInstanceStatus.class);

        VariableProfile profile = query(context, key, activeNodeId, statuses.isEmpty() ? null : statuses,
                null, null, Map.of(), List.of())
                .profileVariables(args.integer("sampleSize", DEFAULT_SAMPLE, 1, VariableProfile.MAX_SAMPLE));

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> undeclared = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int hidden = 0;
        for (VariableProfile.KeyProfile variable : profile.variables()) {
            if (!securityPolicyManager.canRead(latest.id(), context.identity(), variable.key())) {
                hidden++;
                continue;
            }
            seen.add(variable.key());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", variable.key());
            Optional<VariableDeclaration> declaration = declarationOf(versions, variable.key());
            row.put("declared", declaration.isPresent());
            declaration.ifPresentOrElse(d -> {
                putIfPresent(row, "label", d.label());
                putIfPresent(row, "description", d.description());
                putIfPresent(row, "format", d.format());
                putIfPresent(row, "currency", d.currency());
                putIfPresent(row, "catalogNumberFormat", catalogNumberFormat(d));
            }, () -> undeclared.add(variable.key()));
            row.put("presentIn", variable.presentCount());
            row.put("presentPct", percent(variable.presentCount(), profile.sampledInstances()));
            row.put("types", variable.types());
            if (variable.suggestedNumberFormat() != null) {
                Map<String, Object> numeric = new LinkedHashMap<>();
                numeric.put("plain", variable.plainNumeric());
                numeric.put("ptBr", variable.ptBrNumeric());
                numeric.put("suggestedNumberFormat", variable.suggestedNumberFormat());
                row.put("numeric", numeric);
            }
            rows.add(row);
        }
        List<String> declaredNotSeen = new ArrayList<>();
        for (VariableDeclaration declaration : latest.variableDeclarations()) {
            if (!seen.contains(declaration.key())) {
                declaredNotSeen.add(declaration.key());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processDefinitionKey", key);
        putIfPresent(data, "activeNodeId", activeNodeId);
        data.put("sampledInstances", profile.sampledInstances());
        data.put("variables", rows);
        data.put("undeclared", undeclared);
        data.put("declaredNotSeen", declaredNotSeen);
        if (hidden > 0) {
            data.put("hiddenByPolicy", hidden);
        }
        if (!undeclared.isEmpty()) {
            data.put("hint", "Variáveis gravadas e não declaradas no catálogo não têm significado oficial: confirme o "
                    + "sentido com o usuário antes de somar, e sugira declará-las (label, descrição, formato, "
                    + VariableNumberFormat.CATALOG_PROPERTY + ").");
        }
        String summary = profile.sampledInstances() + " instância(s) examinadas de `" + key + "`: " + rows.size()
                + " variáveis, " + undeclared.size() + " fora do catálogo.";
        return new ToolResult(summary, data);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // aggregate_process_variables
    // ---------------------------------------------------------------------------------------------------------------

    private ToolResult aggregate(ToolArgs args, McpCallContext context) {
        String key = args.requiredString("processDefinitionKey");
        List<ProcessDefinition> versions = definitions.versionsOf(key);

        List<VariableAggregation.Metric> metrics = parseMetrics(args);
        VariableNumberFormat fallback = args.enums("numberFormat", VariableNumberFormat.class).stream().findFirst().orElse(null);
        Map<String, VariableNumberFormat> catalogFormats = catalogFormats(versions, metrics);
        VariableAggregation aggregation = parseAggregation(args, metrics, fallback, catalogFormats);
        checkReadable(versions, aggregation, context);

        List<ProcessInstanceStatus> statuses = args.enums("statuses", ProcessInstanceStatus.class);
        if (statuses.isEmpty()) {
            statuses = List.of(ProcessInstanceStatus.ACTIVE);
        }
        Map<String, Object> variableFilter = args.object("variables");
        List<String> variablesExist = args.strings("variablesExist");
        String activeNodeId = args.string("activeNodeId");

        VariableAggregationResult result = query(context, key, activeNodeId, statuses,
                args.instant("startedAfter"), args.instant("startedBefore"), variableFilter, variablesExist)
                .aggregate(aggregation);

        ProcessDefinition latest = versions.get(0);
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("processDefinitionKey", key);
        putIfPresent(filters, "activeNodeId", activeNodeId);
        if (activeNodeId != null) {
            putIfPresent(filters, "activeNodeName", nodeName(versions, activeNodeId));
        }
        filters.put("statuses", statuses);
        putIfPresent(filters, "startedAfter", args.instant("startedAfter"));
        putIfPresent(filters, "startedBefore", args.instant("startedBefore"));
        if (!variableFilter.isEmpty()) {
            filters.put("variables", variableFilter);
        }
        if (!variablesExist.isEmpty()) {
            filters.put("variablesExist", variablesExist);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("filters", filters);
        data.put("numberFormats", formatsView(aggregation, catalogFormats, fallback));
        data.put("instanceCount", result.totals().instanceCount());
        data.put("totals", metricsView(result.totals(), aggregation, versions));
        if (aggregation.groupBy() != VariableAggregation.GroupBy.NONE) {
            data.put("groupBy", aggregation.groupBy());
            putIfPresent(data, "groupByVariable", aggregation.groupByVariable());
            List<Map<String, Object>> groups = new ArrayList<>();
            for (VariableAggregationResult.Group group : result.groups()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", group.key());
                if (aggregation.groupBy() == VariableAggregation.GroupBy.ACTIVE_NODE && group.key() != null) {
                    putIfPresent(row, "nodeName", nodeName(versions, group.key().toString()));
                }
                row.put("instanceCount", group.instanceCount());
                row.put("metrics", metricsView(group, aggregation, versions));
                groups.add(row);
            }
            data.put("groups", groups);
            data.put("groupsTruncated", result.groupsTruncated());
        }
        List<String> notes = notes(result, aggregation, versions);
        if (!notes.isEmpty()) {
            data.put("notes", notes);
        }
        putIfPresent(data, "monitorUrl", links.process(context, latest.id()));

        return new ToolResult(summary(key, filters, result, aggregation), data);
    }

    private ProcessInstanceQuery query(McpCallContext context, String key, String activeNodeId,
                                       List<ProcessInstanceStatus> statuses, java.time.Instant startedAfter,
                                       java.time.Instant startedBefore, Map<String, Object> variableFilter,
                                       List<String> variablesExist) {
        ProcessInstanceSearchRequest request = new ProcessInstanceSearchRequest(
                null, null, List.of(key),
                activeNodeId, null,
                TenantGuard.scopedTenant(context.identity()), null,
                statuses,
                null, null,
                startedAfter, startedBefore,
                variableFilter.isEmpty() ? null : variableFilter,
                variablesExist.isEmpty() ? null : variablesExist,
                "startedAt", false, 0, 1);
        return ProcessInstanceQueryMapper.applyRequest(queryRepository.createProcessInstanceQuery(), request);
    }

    private static List<VariableAggregation.Metric> parseMetrics(ToolArgs args) {
        List<ToolArgs> rawMetrics = args.objects("metrics");
        if (rawMetrics.isEmpty()) {
            throw ToolException.invalidArgument("Informe ao menos uma métrica em `metrics`, ex.: [{\"variable\": \"valorCompra\"}].");
        }
        try {
            return rawMetrics.stream()
                    .map(m -> new VariableAggregation.Metric(m.string("label"), m.requiredString("variable"), m.string("minus")))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw ToolException.invalidArgument(e.getMessage());
        }
    }

    private static VariableAggregation parseAggregation(ToolArgs args, List<VariableAggregation.Metric> metrics,
                                                        VariableNumberFormat fallback,
                                                        Map<String, VariableNumberFormat> catalogFormats) {
        List<VariableAggregation.GroupBy> groupBy = args.enums("groupBy", VariableAggregation.GroupBy.class);
        try {
            return new VariableAggregation(metrics,
                    groupBy.isEmpty() ? VariableAggregation.GroupBy.NONE : groupBy.get(0),
                    args.string("groupByVariable"),
                    args.integer("maxGroups", 20, 1, MAX_GROUPS),
                    fallback, catalogFormats);
        } catch (IllegalArgumentException e) {
            throw ToolException.invalidArgument(e.getMessage());
        }
    }

    /** Formato declarado no catálogo para cada variável medida que o declara (a versão mais nova que a declara). */
    private static Map<String, VariableNumberFormat> catalogFormats(List<ProcessDefinition> versions,
                                                                    List<VariableAggregation.Metric> metrics) {
        Map<String, VariableNumberFormat> formats = new LinkedHashMap<>();
        for (String variable : measuredVariables(metrics)) {
            declarationOf(versions, variable).map(AggregationTools::catalogNumberFormat)
                    .ifPresent(format -> formats.put(variable, format));
        }
        return formats;
    }

    /** Por variável: formato usado e de onde veio (catalog, argument, default) — para o agente explicar a conta. */
    private static Map<String, Object> formatsView(VariableAggregation aggregation,
                                                   Map<String, VariableNumberFormat> catalogFormats,
                                                   VariableNumberFormat fallback) {
        Map<String, Object> view = new LinkedHashMap<>();
        for (String variable : measuredVariables(aggregation.metrics())) {
            String source = catalogFormats.containsKey(variable) ? "catalog" : fallback != null ? "argument" : "default";
            view.put(variable, Map.of("format", aggregation.formatOf(variable), "source", source));
        }
        return view;
    }

    private static Set<String> measuredVariables(List<VariableAggregation.Metric> metrics) {
        Set<String> variables = new LinkedHashSet<>();
        for (VariableAggregation.Metric metric : metrics) {
            variables.add(metric.variable());
            if (metric.isDifference()) {
                variables.add(metric.minusVariable());
            }
        }
        return variables;
    }

    private static VariableNumberFormat catalogNumberFormat(VariableDeclaration declaration) {
        return declaration.extensionProperties() == null ? null
                : VariableNumberFormat.fromCatalog(declaration.extensionProperties().get(VariableNumberFormat.CATALOG_PROPERTY));
    }

    /** Somar é ler: variável que a política esconde desta identidade não entra em conta nenhuma. */
    private void checkReadable(List<ProcessDefinition> versions, VariableAggregation aggregation, McpCallContext context) {
        Set<String> names = new LinkedHashSet<>();
        for (VariableAggregation.Metric metric : aggregation.metrics()) {
            names.add(metric.variable());
            if (metric.isDifference()) {
                names.add(metric.minusVariable());
            }
        }
        if (aggregation.groupByVariable() != null) {
            names.add(aggregation.groupByVariable());
        }
        for (ProcessDefinition version : versions) {
            if (!securityPolicyManager.canRead(version.id(), context.identity(), names)) {
                throw new ToolException(ToolException.FORBIDDEN,
                        "Sem permissão de leitura sobre alguma das variáveis pedidas: " + names);
            }
        }
    }

    private static Map<String, Object> metricsView(VariableAggregationResult.Group group, VariableAggregation aggregation,
                                                   List<ProcessDefinition> versions) {
        Map<String, Object> view = new LinkedHashMap<>();
        for (VariableAggregation.Metric metric : aggregation.metrics()) {
            VariableAggregationResult.MetricValue value = group.metrics().get(metric.label());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sum", value.sum());
            putIfPresent(row, "avg", value.avg() == null ? null : value.avg().setScale(Math.min(value.avg().scale(), 2), RoundingMode.HALF_EVEN));
            putIfPresent(row, "min", value.min());
            putIfPresent(row, "max", value.max());
            row.put("counted", value.numericCount());
            row.put("missing", Math.max(0, group.instanceCount() - value.presentCount()));
            row.put("nonNumeric", value.nonNumericCount());
            putIfPresent(row, "currency", currencyOf(versions, metric));
            view.put(metric.label(), row);
        }
        return view;
    }

    private static String currencyOf(List<ProcessDefinition> versions, VariableAggregation.Metric metric) {
        String currency = declarationOf(versions, metric.variable()).map(VariableDeclaration::currency).orElse(null);
        if (metric.isDifference()) {
            String other = declarationOf(versions, metric.minusVariable()).map(VariableDeclaration::currency).orElse(null);
            return currency != null && currency.equals(other) ? currency : null;
        }
        return currency;
    }

    private static List<String> notes(VariableAggregationResult result, VariableAggregation aggregation,
                                      List<ProcessDefinition> versions) {
        List<String> notes = new ArrayList<>();
        long instances = result.totals().instanceCount();
        if (instances == 0) {
            notes.add("Nenhuma instância casou com os filtros. Confira activeNodeId (describe_process) e statuses.");
            return notes;
        }
        for (VariableAggregation.Metric metric : aggregation.metrics()) {
            VariableAggregationResult.MetricValue value = result.totals().metrics().get(metric.label());
            if (value.presentCount() == 0) {
                notes.add("`" + metric.label() + "`: nenhuma das " + instances + " instâncias tem "
                        + (metric.isDifference() ? "as duas variáveis" : "a variável")
                        + " — confira a chave com profile_process_variables.");
            } else if (value.nonNumericCount() > 0) {
                notes.add("`" + metric.label() + "`: " + value.nonNumericCount() + " instância(s) com valor fora do formato "
                        + "esperado ficaram fora da conta (ver `numberFormats`). Se for a maioria, o formato está errado: "
                        + "veja `suggestedNumberFormat` em profile_process_variables.");
            }
            if (value.presentCount() > 0 && declarationOf(versions, metric.variable()).isEmpty()) {
                notes.add("`" + metric.variable() + "` não está no catálogo do processo: o significado foi inferido pelo "
                        + "nome — diga isso ao usuário.");
            }
        }
        if (aggregation.groupBy() == VariableAggregation.GroupBy.ACTIVE_NODE) {
            notes.add("Agrupado por etapa: uma instância com tokens em mais de uma etapa (paralelo) aparece em cada uma; "
                    + "os totais contam cada instância uma vez.");
        }
        if (result.groupsTruncated()) {
            notes.add("Há mais grupos do que os devolvidos (maxGroups); os totais incluem todos.");
        }
        return notes;
    }

    private static String summary(String key, Map<String, Object> filters, VariableAggregationResult result,
                                  VariableAggregation aggregation) {
        StringBuilder text = new StringBuilder();
        text.append(result.totals().instanceCount()).append(" instância(s) de `").append(key).append('`');
        Object node = filters.containsKey("activeNodeName") ? filters.get("activeNodeName") : filters.get("activeNodeId");
        if (node != null) {
            text.append(" em ").append(node);
        }
        text.append('.');
        for (VariableAggregation.Metric metric : aggregation.metrics()) {
            VariableAggregationResult.MetricValue value = result.totals().metrics().get(metric.label());
            text.append(' ').append(metric.label()).append(": soma ").append(value.sum().toPlainString())
                    .append(" (").append(value.numericCount()).append(" com valor).");
        }
        return text.toString();
    }

    /** Declaração da variável na versão mais recente que a tem (as versões vêm da mais nova para a mais antiga). */
    private static Optional<VariableDeclaration> declarationOf(List<ProcessDefinition> versions, String variable) {
        for (ProcessDefinition version : versions) {
            Optional<VariableDeclaration> declaration = version.findVariableDeclaration(variable);
            if (declaration.isPresent()) {
                return declaration;
            }
        }
        return Optional.empty();
    }

    private static BigDecimal percent(long part, long total) {
        return total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_EVEN);
    }

    private static String nodeName(List<ProcessDefinition> versions, String nodeId) {
        for (ProcessDefinition version : versions) {
            FlowNodeDefinition node = version.flowNodes().get(nodeId);
            if (node != null && node.name() != null && !node.name().isBlank()) {
                return node.name();
            }
        }
        return null;
    }
}
