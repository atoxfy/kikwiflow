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

import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.NodeDescriber;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.form.FormDescriptor;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.variable.FormDescriptorFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/** Ferramentas sobre definições implantadas: catálogo, estrutura do fluxo, formulário de início e saúde. */
public class ProcessDefinitionTools {

    public static final String RESOURCE_URI_PREFIX = "kikwi://process-definitions/";

    private final QueryRepository queryRepository;
    private final DefinitionLookup definitions;
    private final MonitorLinks links;

    public ProcessDefinitionTools(QueryRepository queryRepository, MonitorLinks links) {
        this.queryRepository = queryRepository;
        this.definitions = new DefinitionLookup(queryRepository);
        this.links = links;
    }

    public List<KikwiTool> tools() {
        return List.of(
                KikwiTool.read("list_process_definitions", "Listar processos implantados",
                        "Lista os processos implantados no motor (uma linha por chave, com a versão mais recente). "
                                + "Comece por aqui quando não souber a chave (`key`) de um processo.",
                        object(props("key", string("Filtra por uma chave exata de processo (opcional)."))),
                        this::listProcessDefinitions),
                KikwiTool.read("describe_process", "Descrever um processo",
                        "Explica a estrutura de um processo: nós (tarefas automáticas e o bean executor, tarefas humanas/"
                                + "externas, gateways e de onde vem a decisão, timers), caminhos entre eles, catálogo de "
                                + "variáveis e o contrato de entrada/saída de cada nó — incluindo o prompt e o modelo de "
                                + "nós de IA (kikwi:prompt). Use para entender o que um processo faz antes de investigar "
                                + "instâncias ou incidentes.",
                        object(props(
                                "key", string("Chave do processo."),
                                "version", integer("Versão específica (opcional; padrão = mais recente).", 1, Integer.MAX_VALUE)),
                                "key"),
                        this::describeProcess),
                KikwiTool.read("get_start_form", "Formulário de início",
                        "Mostra o que é preciso para iniciar uma instância do processo: campos, formatos, opções e quais "
                                + "são obrigatórios (o contrato `inputs` do nó de início). Consulte antes de "
                                + "start_process_instance.",
                        object(props("key", string("Chave do processo.")), "key"),
                        this::getStartForm),
                KikwiTool.read("get_process_health", "Saúde de um processo",
                        "Indicadores operacionais da versão mais recente de um processo: instâncias em andamento, "
                                + "falhas, aderência ao SLA e, por nó, onde as instâncias estão paradas. Use para achar "
                                + "gargalos e nós problemáticos.",
                        object(props(
                                "key", string("Chave do processo."),
                                "version", integer("Versão específica (opcional; padrão = mais recente).", 1, Integer.MAX_VALUE)),
                                "key"),
                        this::getProcessHealth));
    }

    private ToolResult listProcessDefinitions(ToolArgs args, McpCallContext context) {
        String keyFilter = args.string("key");
        List<ProcessDefinition> all = keyFilter != null
                ? queryRepository.findAProcessDefinitionsByParams(keyFilter)
                : queryRepository.findAllProcessDefinitions();

        Map<String, List<ProcessDefinition>> byKey = (all == null ? List.<ProcessDefinition>of() : all).stream()
                .filter(definition -> definition.key() != null)
                .collect(Collectors.groupingBy(ProcessDefinition::key, TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        byKey.forEach((key, versions) -> {
            ProcessDefinition latest = versions.stream()
                    .max(Comparator.comparing(ProcessDefinition::version, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key);
            putIfPresent(row, "name", latest.name());
            putIfPresent(row, "description", latest.description());
            row.put("latestVersion", latest.version());
            row.put("versionCount", versions.size());
            row.put("processDefinitionId", latest.id());
            row.put("nodeCount", latest.flowNodes() != null ? latest.flowNodes().size() : 0);
            putIfPresent(row, "sla", latest.sla());
            putIfPresent(row, "monitorUrl", links.process(context, latest.id()));
            rows.add(row);
        });
        return ToolResult.of(rows.size() + " processo(s) implantado(s).", Map.of("processDefinitions", rows));
    }

    private ToolResult describeProcess(ToolArgs args, McpCallContext context) {
        ProcessDefinition definition = definitions.byKey(args.requiredString("key"), args.optionalInteger("version"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", definition.key());
        result.put("processDefinitionId", definition.id());
        result.put("version", definition.version());
        putIfPresent(result, "name", definition.name());
        putIfPresent(result, "description", definition.description());
        putIfPresent(result, "sla", definition.sla());
        putIfPresent(result, "documentation", definition.extensionProperties() != null
                ? definition.extensionProperties().get(NodeDescriber.DOCUMENTATION_KEY) : null);
        result.put("startNode", definition.defaultStartPoint());

        List<Map<String, Object>> variables = definition.variableDeclarations() == null ? List.of()
                : definition.variableDeclarations().stream().map(NodeDescriber::declaration).toList();
        result.put("variables", variables);

        List<Map<String, Object>> nodes = new ArrayList<>();
        if (definition.flowNodes() != null) {
            definition.flowNodes().forEach((nodeKey, node) -> nodes.add(NodeDescriber.describe(definition, nodeKey, node)));
        }
        result.put("nodes", nodes);
        result.put("resourceUri", RESOURCE_URI_PREFIX + definition.key() + "/versions/" + definition.version());
        putIfPresent(result, "monitorUrl", links.process(context, definition.id()));

        return ToolResult.of("Processo `" + definition.key() + "` v" + definition.version() + ": " + nodes.size()
                + " nós, " + variables.size() + " variáveis declaradas.", result);
    }

    private ToolResult getStartForm(ToolArgs args, McpCallContext context) {
        ProcessDefinition definition = definitions.byKey(args.requiredString("key"), null);
        FormDescriptor form = FormDescriptorFactory.forStart(definition);
        long required = form.fields() == null ? 0 : form.fields().stream().filter(field -> field.required()).count();
        return ToolResult.of("Formulário de início de `" + definition.key() + "`: "
                + (form.fields() == null ? 0 : form.fields().size()) + " campo(s), " + required + " obrigatório(s).", form);
    }

    private ToolResult getProcessHealth(ToolArgs args, McpCallContext context) {
        ProcessDefinition definition = definitions.byKey(args.requiredString("key"), args.optionalInteger("version"));
        KKFMetrics macro = queryRepository.getProcessMacroMetrics(definition.id());
        Map<String, KKFMetrics> byNode = queryRepository.getMetricsByNodeForProcessDefinition(definition.id());

        List<Map<String, Object>> nodes = new ArrayList<>();
        if (byNode != null && definition.flowNodes() != null) {
            byNode.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && (nonZero(entry.getValue().running()) || nonZero(entry.getValue().fail())))
                    .sorted(Comparator.comparing((Map.Entry<String, KKFMetrics> entry) -> valueOf(entry.getValue().running())).reversed())
                    .forEach(entry -> {
                        Map<String, Object> node = new LinkedHashMap<>();
                        node.put("nodeId", entry.getKey());
                        var flowNode = definition.flowNodes().get(entry.getKey());
                        if (flowNode != null) {
                            putIfPresent(node, "name", flowNode.name());
                            putIfPresent(node, "type", flowNode.type());
                        }
                        node.put("waitingHere", valueOf(entry.getValue().running()));
                        node.put("failed", valueOf(entry.getValue().fail()));
                        putIfPresent(node, "slaCompliancePercent", entry.getValue().sla());
                        nodes.add(node);
                    });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", definition.key());
        result.put("version", definition.version());
        result.put("processDefinitionId", definition.id());
        // Mesmos agregados do endpoint /pulse do REST (não segmentados por tenant).
        result.put("scope", "all-tenants");
        result.put("activeInstances",queryRepository.countProcessInstancesByProcessDefinition(definition.id()));
        result.put("openIncidents", queryRepository.countOpenIncidentsByProcessDefinition(definition.id()));
        if (macro != null) {
            result.put("running", valueOf(macro.running()));
            result.put("failed", valueOf(macro.fail()));
            putIfPresent(result, "slaCompliancePercent", macro.sla());
        }
        result.put("nodesWithActivity", nodes);
        putIfPresent(result, "monitorUrl", links.process(context, definition.id()));

        return ToolResult.of("`" + definition.key() + "` v" + definition.version() + ": " + result.get("activeInstances")
                + " instância(s) ativa(s), " + result.get("openIncidents") + " incidente(s) aberto(s).", result);
    }

    private static boolean nonZero(Long value) {
        return value != null && value != 0L;
    }

    private static long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
