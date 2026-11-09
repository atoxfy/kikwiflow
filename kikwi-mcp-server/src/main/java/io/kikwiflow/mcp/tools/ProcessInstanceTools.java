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
import io.kikwiflow.management.dtos.ProcessInstanceSnapshot;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.history.InstanceTrace;
import io.kikwiflow.management.mapper.ProcessInstanceQueryMapper;
import io.kikwiflow.management.service.InstanceTraceService;
import io.kikwiflow.management.service.ProcessInstanceSnapshotService;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.TenantGuard;
import io.kikwiflow.mcp.support.VariablePresenter;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.kikwiflow.mcp.Schema.dateTime;
import static io.kikwiflow.mcp.Schema.enumArray;
import static io.kikwiflow.mcp.Schema.freeObject;
import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.Schema.stringArray;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/** Ferramentas sobre instâncias: busca, estado atual e linha do tempo (inclusive de instâncias já encerradas). */
public class ProcessInstanceTools {

    static final int MAX_PAGE_SIZE = 50;

    private final QueryRepository queryRepository;
    private final ProcessInstanceSnapshotService snapshotService;
    private final InstanceTraceService traceService;
    private final DefinitionLookup definitions;
    private final VariablePresenter variables;
    private final MonitorLinks links;

    public ProcessInstanceTools(QueryRepository queryRepository, ProcessInstanceSnapshotService snapshotService,
                                InstanceTraceService traceService, VariablePresenter variables, MonitorLinks links) {
        this.queryRepository = queryRepository;
        this.snapshotService = snapshotService;
        this.traceService = traceService;
        this.definitions = new DefinitionLookup(queryRepository);
        this.variables = variables;
        this.links = links;
    }

    public List<KikwiTool> tools() {
        return List.of(
                KikwiTool.read("search_process_instances", "Buscar instâncias",
                        "Busca instâncias em execução ou concluídas que ainda estão na base operacional, com filtros "
                                + "combinados por AND. Para \"onde estão paradas as propostas X\" filtre por "
                                + "`activeNodeId`; para achar pelo identificador de negócio use `businessKey`. Instâncias "
                                + "encerradas há tempo podem só existir no histórico (get_instance_timeline).",
                        object(props(
                                "processDefinitionKey", string("Chave do processo (todas as versões)."),
                                "businessKey", string("Identificador de negócio exato (ex.: número da proposta)."),
                                "statuses", enumArray("Status desejados (padrão: todos).", ProcessInstanceStatus.values()),
                                "activeNodeId", string("Só instâncias com token ativo neste nó (id do nó, ver describe_process)."),
                                "startedAfter", dateTime("Iniciadas a partir de (ISO-8601 UTC)."),
                                "startedBefore", dateTime("Iniciadas antes de (ISO-8601 UTC)."),
                                "variables", freeObject("Igualdade exata de variáveis, ex.: {\"cpf\": \"123\"}."),
                                "variablesExist", stringArray("Só instâncias que possuem estas variáveis."),
                                "page", integer("Página (0-based).", 0, 10_000),
                                "size", integer("Itens por página (padrão 20).", 1, MAX_PAGE_SIZE))),
                        this::searchProcessInstances),
                KikwiTool.read("get_process_instance", "Estado de uma instância",
                        "Estado atual de uma instância viva: status, onde está o token, tarefas automáticas pendentes "
                                + "(com tentativas restantes), tarefas humanas/externas abertas e responsável, incidentes "
                                + "abertos e variáveis. Inclui links para abrir a instância no Kikwiflow Monitor.",
                        object(props("processInstanceId", string("Id da instância.")), "processInstanceId"),
                        this::getProcessInstance),
                KikwiTool.read("get_instance_timeline", "Linha do tempo da instância",
                        "Histórico auditável de uma instância — funciona também depois que ela terminou: caminho "
                                + "percorrido, decisões de gateway (resposta e de onde veio), ações humanas (quem concluiu "
                                + "e o que submeteu), eventos em ordem e variáveis finais. Use para responder \"o que "
                                + "aconteceu\", \"quem aprovou\" e \"por que seguiu por este caminho\".",
                        object(props(
                                "processInstanceId", string("Id da instância."),
                                "maxEvents", integer("Quantos eventos mais recentes incluir (padrão 50).", 0, 500)),
                                "processInstanceId"),
                        this::getInstanceTimeline));
    }

    private ToolResult searchProcessInstances(ToolArgs args, McpCallContext context) {
        String key = args.string("processDefinitionKey");
        if (key != null) {
            definitions.versionsOf(key); // NOT_FOUND orientado em vez de lista vazia silenciosa
        }
        Map<String, Object> variableFilter = args.object("variables");
        List<String> variablesExist = args.strings("variablesExist");
        List<ProcessInstanceStatus> statuses = args.enums("statuses", ProcessInstanceStatus.class);

        ProcessInstanceSearchRequest request = new ProcessInstanceSearchRequest(
                null, null, key != null ? List.of(key) : null,
                args.string("activeNodeId"), null,
                TenantGuard.scopedTenant(context.identity()), null,
                statuses.isEmpty() ? null : statuses,
                args.string("businessKey"), null,
                args.instant("startedAfter"), args.instant("startedBefore"),
                variableFilter.isEmpty() ? null : variableFilter,
                variablesExist.isEmpty() ? null : variablesExist,
                "startedAt", false,
                args.integer("page", 0, 0, 10_000),
                args.integer("size", 20, 1, MAX_PAGE_SIZE));

        PageResult<ProcessInstanceSummary> page = ProcessInstanceQueryMapper
                .applyRequest(queryRepository.createProcessInstanceQuery(), request)
                .listSummary();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProcessInstanceSummary summary : page.content()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("processInstanceId", summary.id());
            putIfPresent(row, "businessKey", summary.businessKey());
            row.put("status", summary.status());
            row.put("processDefinitionId", summary.processDefinitionId());
            row.put("startedAt", summary.startedAt());
            putIfPresent(row, "endedAt", summary.endedAt());
            if (summary.activeNodes() != null && !summary.activeNodes().isEmpty()) {
                row.put("activeNodes", summary.activeNodes());
            }
            putIfPresent(row, "parentInstanceId", summary.parentInstanceId());
            putIfPresent(row, "monitorUrl", links.instance(context, summary.processDefinitionId(), summary.id()));
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalElements", page.totalElements());
        result.put("page", page.page());
        result.put("totalPages", page.totalPages());
        result.put("instances", rows);
        String more = page.page() + 1 < page.totalPages() ? " Há mais páginas (use `page`)." : "";
        return ToolResult.of(page.totalElements() + " instância(s) encontrada(s)." + more, result);
    }

    private ToolResult getProcessInstance(ToolArgs args, McpCallContext context) {
        String id = args.requiredString("processInstanceId");
        ProcessInstanceSnapshot snapshot;
        try {
            snapshot = snapshotService.getSnapshot(id);
        } catch (NotFoundException e) {
            throw ToolException.notFound("Instância `" + id + "` não está na base operacional. Se ela já terminou, "
                    + "use get_instance_timeline para o histórico.");
        }
        ProcessInstance instance = snapshot.instance();
        TenantGuard.checkAccess(context.identity(), instance.tenantId(), "Instância `" + id + "` não encontrada.");
        ProcessDefinition definition = definitions.byId(instance.processDefinitionId()).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", instance.id());
        putIfPresent(result, "businessKey", instance.businessKey());
        result.put("status", instance.status());
        if (definition != null) {
            result.put("processDefinitionKey", definition.key());
            result.put("processDefinitionVersion", definition.version());
        }
        result.put("processDefinitionId", instance.processDefinitionId());
        putIfPresent(result, "tenantId", instance.tenantId());
        putIfPresent(result, "origin", instance.origin());
        putIfPresent(result, "businessValue", instance.businessValue());
        result.put("startedAt", instance.startedAt());
        putIfPresent(result, "endedAt", instance.endedAt());
        result.put("activeNodes", instance.activeNodes() != null ? instance.activeNodes() : Map.of());
        putIfPresent(result, "parentInstanceId", instance.parentInstanceId());

        List<Map<String, Object>> automatic = new ArrayList<>();
        for (ExecutableTask task : nullSafe(snapshot.executableTasks())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", task.id());
            row.put("nodeId", task.taskDefinitionId());
            putIfPresent(row, "name", task.name());
            putIfPresent(row, "type", task.type());
            putIfPresent(row, "status", task.status());
            putIfPresent(row, "executions", task.executions());
            putIfPresent(row, "retriesLeft", task.retries());
            putIfPresent(row, "dueDate", task.dueDate());
            putIfPresent(row, "lastError", task.error() != null ? variables.truncate(task.error()) : null);
            automatic.add(row);
        }
        result.put("pendingAutomaticTasks", automatic);

        List<Map<String, Object>> waiting = new ArrayList<>();
        for (ExternalTask task : nullSafe(snapshot.externalTasks())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", task.id());
            row.put("nodeId", task.taskDefinitionId());
            putIfPresent(row, "name", task.displayName() != null ? task.displayName() : task.name());
            putIfPresent(row, "type", task.type());
            putIfPresent(row, "status", task.status());
            putIfPresent(row, "assignee", task.assignee());
            putIfPresent(row, "createdAt", task.createdAt());
            putIfPresent(row, "correlationKey", task.correlationKey());
            waiting.add(row);
        }
        result.put("waitingTasks", waiting);

        List<Map<String, Object>> incidents = new ArrayList<>();
        for (Incident incident : nullSafe(snapshot.incidents())) {
            incidents.add(IncidentTools.incidentRow(incident, variables, links, context));
        }
        result.put("incidents", incidents);
        if (snapshot.eventCatcherWaitStatus() != null && !snapshot.eventCatcherWaitStatus().isEmpty()) {
            result.put("eventWaits", snapshot.eventCatcherWaitStatus());
        }
        result.put("variables", variables.present(instance.processDefinitionId(), context.identity(), instance.variables()));
        putIfPresent(result, "monitorUrl", links.instance(context, instance.processDefinitionId(), instance.id()));
        putIfPresent(result, "historyUrl", links.history(context, instance.id()));

        long openIncidents = incidents.stream().filter(row -> "OPEN".equals(String.valueOf(row.get("status")))).count();
        return ToolResult.of("Instância `" + instance.id() + "` (" + instance.status() + "), nós ativos: "
                + instance.activeNodes().keySet() + ", " + openIncidents + " incidente(s) aberto(s).", result);
    }

    private ToolResult getInstanceTimeline(ToolArgs args, McpCallContext context) {
        String id = args.requiredString("processInstanceId");
        int maxEvents = args.integer("maxEvents", 50, 0, 500);
        InstanceTrace trace;
        try {
            trace = traceService.getTrace(id, context.identity());
        } catch (NotFoundException e) {
            throw ToolException.notFound("Nenhuma instância nem histórico com id `" + id + "`. Se o histórico "
                    + "estiver vazio, confirme que kikwiflow.outbox.events-enabled está ligado no host.");
        }
        String tenant = trace.instance() != null ? trace.instance().tenantId() : null;
        TenantGuard.checkAccess(context.identity(), tenant, "Instância `" + id + "` não encontrada.");

        List<HistoryEventSummary> allEvents = trace.events() != null ? trace.events() : List.of();
        List<Map<String, Object>> events = new ArrayList<>();
        for (HistoryEventSummary event : allEvents.subList(Math.max(0, allEvents.size() - maxEvents), allEvents.size())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", event.timestamp());
            row.put("sequence", event.sequence());
            row.put("eventType", event.eventType());
            putIfPresent(row, "actorId", event.actorId());
            row.put("details", event.payload());
            events.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", trace.instance());
        result.put("activeNodes", trace.activeNodes());
        result.put("visitedNodes", trace.visitedNodes());
        result.put("decisions", trace.decisions());
        result.put("humanActions", trace.humanActions());
        result.put("totalEvents", allEvents.size());
        result.put("events", events);
        if (trace.finalVariables() != null) {
            result.put("variables", variables.presentAlreadyMasked(trace.finalVariables()));
        }
        String definitionId = trace.instance() != null ? trace.instance().processDefinitionId() : null;
        putIfPresent(result, "monitorUrl", links.instance(context, definitionId, id));
        putIfPresent(result, "historyUrl", links.history(context, id));

        int decisions = trace.decisions() != null ? trace.decisions().size() : 0;
        int humanActions = trace.humanActions() != null ? trace.humanActions().size() : 0;
        return ToolResult.of("Linha do tempo de `" + id + "`: " + allEvents.size() + " evento(s), " + decisions
                + " decisão(ões), " + humanActions + " ação(ões) humana(s).", result);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
