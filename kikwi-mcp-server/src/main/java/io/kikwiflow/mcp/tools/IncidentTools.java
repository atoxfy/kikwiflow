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

import io.kikwiflow.management.dtos.IncidentSearchRequest;
import io.kikwiflow.management.mapper.HistoryEventSummaryMapper;
import io.kikwiflow.management.mapper.IncidentQueryMapper;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.NodeDescriber;
import io.kikwiflow.mcp.support.TenantGuard;
import io.kikwiflow.mcp.support.VariablePresenter;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kikwiflow.mcp.Schema.dateTime;
import static io.kikwiflow.mcp.Schema.enumArray;
import static io.kikwiflow.mcp.Schema.enumOf;
import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/** Busca, agrupamento por causa e diagnóstico de incidentes. */
public class IncidentTools {

    static final int MAX_PAGE_SIZE = 50;
    static final int GROUPING_SAMPLE = 100;
    private static final int STACK_FRAMES = 8;
    private static final int RECENT_EVENTS = 10;

    enum GroupBy { NONE, CAUSE }

    private final QueryRepository queryRepository;
    private final VariableSecurityPolicyManager securityPolicyManager;
    private final DefinitionLookup definitions;
    private final VariablePresenter variables;
    private final MonitorLinks links;

    public IncidentTools(QueryRepository queryRepository, VariableSecurityPolicyManager securityPolicyManager,
                         VariablePresenter variables, MonitorLinks links) {
        this.queryRepository = queryRepository;
        this.securityPolicyManager = securityPolicyManager;
        this.definitions = new DefinitionLookup(queryRepository);
        this.variables = variables;
        this.links = links;
    }

    public List<KikwiTool> tools() {
        return List.of(
                KikwiTool.read("search_incidents", "Buscar incidentes",
                        "Lista incidentes (tarefas automáticas que esgotaram as tentativas). Padrão: só abertos (OPEN). "
                                + "Com `groupBy=CAUSE` agrupa por nó + mensagem de erro — use para reconhecer uma "
                                + "\"tempestade\" de incidentes com a mesma causa antes de diagnosticar um a um.",
                        object(props(
                                "processDefinitionKey", string("Chave do processo (todas as versões)."),
                                "nodeId", string("Id do nó que falhou (taskDefinitionId)."),
                                "processInstanceId", string("Só incidentes desta instância."),
                                "statuses", enumArray("Status (padrão: OPEN).", IncidentStatus.values()),
                                "message", string("Trecho da mensagem de erro."),
                                "createdAfter", dateTime("Criados a partir de (ISO-8601 UTC)."),
                                "createdBefore", dateTime("Criados antes de (ISO-8601 UTC)."),
                                "groupBy", enumOf("NONE (lista) ou CAUSE (agrupado).", GroupBy.values()),
                                "page", integer("Página (0-based), só para groupBy=NONE.", 0, 10_000),
                                "size", integer("Itens por página (padrão 20).", 1, MAX_PAGE_SIZE))),
                        this::searchIncidents),
                KikwiTool.read("diagnose_incident", "Diagnosticar incidente",
                        "Reúne em uma chamada tudo para explicar um incidente: erro e causa raiz (stack trace resumido), "
                                + "o nó que falhou (executor, política de retry, contrato de entrada/saída, prompt se for "
                                + "nó de IA), as variáveis relevantes da instância, os últimos eventos, quantos incidentes "
                                + "iguais existem e uma recomendação sobre reprocessar. Comece por aqui ao investigar falhas.",
                        object(props("incidentId", string("Id do incidente.")), "incidentId"),
                        this::diagnoseIncident));
    }

    private ToolResult searchIncidents(ToolArgs args, McpCallContext context) {
        String key = args.string("processDefinitionKey");
        List<String> definitionIds = key != null ? definitions.versionIdsOf(key) : null;
        List<IncidentStatus> statuses = args.enums("statuses", IncidentStatus.class);
        String groupByArg = args.string("groupBy");
        GroupBy groupBy = groupByArg == null ? GroupBy.NONE : args.enums("groupBy", GroupBy.class).get(0);
        boolean grouped = groupBy == GroupBy.CAUSE;

        IncidentSearchRequest request = new IncidentSearchRequest(
                null, definitionIds,
                TenantGuard.scopedTenant(context.identity()), null,
                args.string("nodeId"), args.string("processInstanceId"),
                statuses.isEmpty() ? List.of(IncidentStatus.OPEN) : statuses,
                null, args.string("message"),
                args.instant("createdAfter"), args.instant("createdBefore"),
                "createdAt", false,
                grouped ? 0 : args.integer("page", 0, 0, 10_000),
                grouped ? GROUPING_SAMPLE : args.integer("size", 20, 1, MAX_PAGE_SIZE));

        PageResult<Incident> page = IncidentQueryMapper.applyRequest(queryRepository.createIncidentQuery(), request).list();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalElements", page.totalElements());
        if (!grouped) {
            result.put("page", page.page());
            result.put("totalPages", page.totalPages());
            result.put("incidents", page.content().stream().map(incident -> incidentRow(incident, variables, links, context)).toList());
            return ToolResult.of(page.totalElements() + " incidente(s) encontrado(s).", result);
        }

        Map<String, List<Incident>> groups = page.content().stream().collect(Collectors.groupingBy(
                incident -> incident.processDefinitionId() + "|" + incident.taskDefinitionId() + "|" + firstLine(incident.message()),
                LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        groups.values().stream()
                .sorted((a, b) -> Integer.compare(b.size(), a.size()))
                .forEach(members -> {
                    Incident sample = members.get(0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("nodeId", sample.taskDefinitionId());
                    row.put("processDefinitionId", sample.processDefinitionId());
                    row.put("cause", variables.truncate(firstLine(sample.message())));
                    row.put("count", members.size());
                    row.put("sampleIncidentIds", members.stream().limit(5).map(Incident::id).toList());
                    row.put("oldestCreatedAt", members.get(members.size() - 1).createdAt());
                    row.put("newestCreatedAt", sample.createdAt());
                    rows.add(row);
                });
        result.put("groups", rows);
        if (page.totalElements() > GROUPING_SAMPLE) {
            result.put("note", "Agrupamento calculado sobre os " + GROUPING_SAMPLE + " incidentes mais recentes de "
                    + page.totalElements() + ". Refine os filtros para ver o restante.");
        }
        return ToolResult.of(page.totalElements() + " incidente(s) em " + rows.size() + " causa(s) distinta(s).", result);
    }

    private ToolResult diagnoseIncident(ToolArgs args, McpCallContext context) {
        String incidentId = args.requiredString("incidentId");
        Incident incident = queryRepository.findIncidentById(incidentId)
                .orElseThrow(() -> ToolException.notFound("Incidente `" + incidentId + "` não encontrado. Use search_incidents."));
        Optional<ProcessInstance> instance = queryRepository.findProcessInstanceById(incident.processInstanceId());
        TenantGuard.checkAccess(context.identity(), instance.map(ProcessInstance::tenantId).orElse(null),
                "Incidente `" + incidentId + "` não encontrado.");

        ProcessDefinition definition = definitions.byId(incident.processDefinitionId()).orElse(null);
        FlowNodeDefinition node = definition != null && definition.flowNodes() != null
                ? definition.flowNodes().get(incident.taskDefinitionId()) : null;
        Optional<ExecutableTask> task = incident.executionId() != null
                ? queryRepository.findExecutableTaskById(incident.executionId()) : Optional.empty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incident", incidentRow(incident, variables, links, context));
        result.put("error", errorSummary(incident));

        if (definition != null) {
            result.put("process", Map.of("key", definition.key(), "version", definition.version(),
                    "processDefinitionId", definition.id()));
        }
        if (node != null) {
            result.put("failedNode", NodeDescriber.describe(definition, incident.taskDefinitionId(), node));
        }
        task.ifPresent(failedTask -> {
            Map<String, Object> taskInfo = new LinkedHashMap<>();
            taskInfo.put("id", failedTask.id());
            putIfPresent(taskInfo, "status", failedTask.status());
            putIfPresent(taskInfo, "executions", failedTask.executions());
            putIfPresent(taskInfo, "retriesLeft", failedTask.retries());
            putIfPresent(taskInfo, "retryPolicy", failedTask.retryPolicy());
            result.put("failedTask", taskInfo);
        });

        instance.ifPresent(live -> {
            Map<String, Object> visible = variables.present(live.processDefinitionId(), context.identity(), live.variables());
            Map<String, Object> instanceInfo = new LinkedHashMap<>();
            instanceInfo.put("processInstanceId", live.id());
            putIfPresent(instanceInfo, "businessKey", live.businessKey());
            instanceInfo.put("status", live.status());
            instanceInfo.put("activeNodes", live.activeNodes());
            instanceInfo.put("relevantVariables", relevantVariables(node, visible));
            result.put("instance", instanceInfo);
        });

        result.put("recentEvents", recentEvents(incident.processInstanceId(), context));

        long similarOpen = countSimilarOpen(incident, context);
        result.put("similarOpenIncidents", similarOpen);
        boolean retryable = incident.status() == IncidentStatus.OPEN;
        result.put("retryable", retryable);
        result.put("assessment", assess(incident, similarOpen, retryable));
        putIfPresent(result, "historyUrl", links.history(context, incident.processInstanceId()));

        return ToolResult.of("Incidente `" + incident.id() + "` no nó `" + incident.taskDefinitionId() + "`: "
                + firstLine(incident.message()) + (similarOpen > 1 ? " (" + similarOpen + " abertos com a mesma causa)" : ""), result);
    }

    static Map<String, Object> incidentRow(Incident incident, VariablePresenter variables, MonitorLinks links, McpCallContext context) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("incidentId", incident.id());
        row.put("status", incident.status());
        row.put("nodeId", incident.taskDefinitionId());
        row.put("processInstanceId", incident.processInstanceId());
        row.put("processDefinitionId", incident.processDefinitionId());
        putIfPresent(row, "type", incident.type());
        putIfPresent(row, "message", variables.truncate(incident.message()));
        row.put("createdAt", incident.createdAt());
        putIfPresent(row, "monitorUrl", links.instance(context, incident.processDefinitionId(), incident.processInstanceId()));
        return row;
    }

    private Map<String, Object> errorSummary(Incident incident) {
        Map<String, Object> error = new LinkedHashMap<>();
        putIfPresent(error, "message", variables.truncate(incident.message()));
        String stackTrace = incident.stackTrace();
        if (stackTrace == null || stackTrace.isBlank()) {
            return error;
        }
        List<String> lines = stackTrace.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        error.put("exception", lines.get(0));
        lines.stream().filter(line -> line.startsWith("Caused by:")).reduce((first, second) -> second)
                .ifPresent(rootCause -> error.put("rootCause", rootCause));
        error.put("topFrames", lines.stream().filter(line -> line.startsWith("at ")).limit(STACK_FRAMES).toList());
        return error;
    }

    /** Variáveis do contrato do nó (inputs/outputs) quando declarado; senão todas, já truncadas. */
    private static Map<String, Object> relevantVariables(FlowNodeDefinition node, Map<String, Object> visible) {
        if (!(node instanceof VariableBindingAware aware)) {
            return visible;
        }
        VariableBindings bindings = aware.variableBindingsOrEmpty();
        if (bindings.inputs().isEmpty() && bindings.outputs().isEmpty()) {
            return visible;
        }
        Map<String, Object> relevant = new LinkedHashMap<>();
        for (VariableBinding binding : bindings.inputs()) {
            relevant.put(binding.variable(), visible.get(binding.variable()));
        }
        for (VariableBinding binding : bindings.outputs()) {
            relevant.put(binding.variable(), visible.get(binding.variable()));
        }
        return relevant;
    }

    private List<Map<String, Object>> recentEvents(String processInstanceId, McpCallContext context) {
        List<OutboxEventEntity> raw = queryRepository.findEventHistoryByProcessInstanceId(processInstanceId);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (OutboxEventEntity entity : raw.subList(Math.max(0, raw.size() - RECENT_EVENTS), raw.size())) {
            HistoryEventSummary event = HistoryEventSummaryMapper.from(entity, securityPolicyManager, context.identity());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", event.timestamp());
            row.put("eventType", event.eventType());
            putIfPresent(row, "actorId", event.actorId());
            events.add(row);
        }
        return events;
    }

    private long countSimilarOpen(Incident incident, McpCallContext context) {
        IncidentSearchRequest request = new IncidentSearchRequest(
                incident.processDefinitionId(), null, TenantGuard.scopedTenant(context.identity()), null,
                incident.taskDefinitionId(), null, List.of(IncidentStatus.OPEN), null, null,
                null, null, null, null, 0, 1);
        return IncidentQueryMapper.applyRequest(queryRepository.createIncidentQuery(), request).count();
    }

    /** Heurística conservadora: orienta o agente, não decide por ele. */
    static Map<String, Object> assess(Incident incident, long similarOpen, boolean retryable) {
        String text = ((incident.message() == null ? "" : incident.message()) + "\n"
                + (incident.stackTrace() == null ? "" : incident.stackTrace())).toLowerCase(Locale.ROOT);
        String category;
        String recommendation;
        if (!retryable) {
            category = "ALREADY_RESOLVED";
            recommendation = "Incidente já resolvido; nada a reprocessar. Veja a linha do tempo para o desfecho.";
        } else if (containsAny(text, "variablevalidationexception", "out-of-contract", "is required", "invalid_format")) {
            category = "CONTRACT_VIOLATION";
            recommendation = "O executor produziu (ou recebeu) variáveis fora do contrato do nó. Um retry só resolve se "
                    + "a saída mudar (ex.: resposta não determinística de LLM); caso contrário corrija o executor ou o dado.";
        } else if (containsAny(text, "timeout", "timed out", "connection", "connect", "unavailable", " 503", " 502",
                " 504", "429", "socket", "ioexception", "temporarily")) {
            category = "TRANSIENT";
            recommendation = "Erro com cara de indisponibilidade temporária de dependência. Se a dependência já voltou, "
                    + "reprocessar é razoável" + (similarOpen > 1 ? " — em lote, com retry_incidents filtrando pelo nó." : ".");
        } else if (containsAny(text, "nullpointerexception", "classcastexception", "illegalargumentexception",
                "numberformatexception", "indexoutofbounds", "nosuchbeandefinition")) {
            category = "LIKELY_BUG_OR_BAD_DATA";
            recommendation = "Parece defeito de código ou dado inválido; reprocessar sem correção tende a falhar de novo. "
                    + "Corrija a causa (ou o dado da instância) antes do retry.";
        } else {
            category = "UNKNOWN";
            recommendation = "Causa não classificada automaticamente. Leia rootCause/topFrames e o contrato do nó antes de reprocessar.";
        }
        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("category", category);
        assessment.put("recommendation", recommendation);
        if (similarOpen > 1) {
            assessment.put("systemic", "Há " + similarOpen + " incidentes abertos no mesmo nó desta versão: provável causa comum.");
        }
        return assessment;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
