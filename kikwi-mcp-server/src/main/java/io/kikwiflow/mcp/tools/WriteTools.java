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

import io.kikwiflow.api.dto.ProcessInstanceStartRequest;
import io.kikwiflow.management.dtos.IncidentRetryRequest;
import io.kikwiflow.management.dtos.IncidentRetryResponse;
import io.kikwiflow.management.dtos.IncidentRetryResult;
import io.kikwiflow.management.dtos.IncidentSearchRequest;
import io.kikwiflow.management.dtos.ProcessInstanceSearchRequest;
import io.kikwiflow.management.mapper.ProcessInstanceQueryMapper;
import io.kikwiflow.management.service.IncidentRetryService;
import io.kikwiflow.management.service.ProcessStartService;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.ConfirmationTokenService;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.TenantGuard;
import io.kikwiflow.mcp.support.VariablePresenter;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.variable.VariableContractValidator;
import io.kikwiflow.variable.VariableValidationError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.kikwiflow.mcp.Schema.bool;
import static io.kikwiflow.mcp.Schema.dateTime;
import static io.kikwiflow.mcp.Schema.freeObject;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.Schema.stringArray;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/**
 * Ferramentas que mudam estado. Só registradas com {@code kikwiflow.mcp.write-tools.enabled=true}; passam pelos
 * mesmos serviços do REST (mesma validação de contrato, mesmo outbox com {@code actorId}). O retry exige preview +
 * token de confirmação; o início valida o contrato antes e recusa businessKey duplicado em instância ativa.
 */
public class WriteTools {

    public static final String ORIGIN = "mcp";
    static final String START_TOOL = "start_process_instance";
    static final String RETRY_TOOL = "retry_incidents";

    private final QueryRepository queryRepository;
    private final ProcessStartService processStartService;
    private final IncidentRetryService incidentRetryService;
    private final ConfirmationTokenService confirmationTokens;
    private final VariableContractValidator contractValidator;
    private final DefinitionLookup definitions;
    private final VariablePresenter variables;
    private final MonitorLinks links;
    private final int maxBatchRetry;

    public WriteTools(QueryRepository queryRepository, ProcessStartService processStartService,
                      IncidentRetryService incidentRetryService, ConfirmationTokenService confirmationTokens,
                      VariablePresenter variables, MonitorLinks links, int maxBatchRetry) {
        this.queryRepository = queryRepository;
        this.processStartService = processStartService;
        this.incidentRetryService = incidentRetryService;
        this.confirmationTokens = confirmationTokens;
        this.contractValidator = new VariableContractValidator();
        this.definitions = new DefinitionLookup(queryRepository);
        this.variables = variables;
        this.links = links;
        this.maxBatchRetry = maxBatchRetry;
    }

    public List<KikwiTool> tools() {
        return List.of(
                new KikwiTool(START_TOOL, "Iniciar instância",
                        "Inicia uma instância de processo (versão mais recente) em nome do usuário autenticado. "
                                + "Consulte get_start_form antes para saber as variáveis exigidas. Use `dryRun=true` para "
                                + "só validar o contrato de entrada sem iniciar. Recusa se já houver instância ATIVA com o "
                                + "mesmo businessKey neste processo (passe `allowDuplicate=true` só se o usuário pediu).",
                        object(props(
                                "key", string("Chave do processo."),
                                "businessKey", string("Identificador de negócio (ex.: número da proposta). Obrigatório."),
                                "variables", freeObject("Variáveis de início, nome → valor JSON simples, ex.: {\"cpf\": \"123\", \"valor\": 1500.5}."),
                                "businessValue", string("Valor de negócio (decimal, opcional)."),
                                "tenant", string("Tenant (ignorado se a identidade já tiver um tenant fixo)."),
                                "dryRun", bool("Só valida; não inicia."),
                                "allowDuplicate", bool("Permite iniciar mesmo com instância ativa de mesmo businessKey.")),
                                "key", "businessKey"),
                        false, false, false, true, this::startProcessInstance),
                new KikwiTool(RETRY_TOOL, "Reprocessar incidentes",
                        "Reprocessa incidentes abertos em DUAS etapas. 1) Sem `confirmationToken`: informe `incidentIds` "
                                + "ou filtros — nada é executado; devolve a lista do que seria reprocessado e um "
                                + "`confirmationToken`. Mostre essa lista ao usuário e peça confirmação. 2) Com o usuário "
                                + "de acordo, chame de novo passando SOMENTE `confirmationToken`: executa exatamente os "
                                + "incidentes do preview. Máximo de " + "%d" + " por lote. Investigue a causa "
                                + "(diagnose_incident) antes: reprocessar um defeito só gera novos incidentes.",
                        object(props(
                                "incidentIds", stringArray("Ids dos incidentes (etapa 1)."),
                                "processDefinitionKey", string("Filtro por processo (etapa 1)."),
                                "nodeId", string("Filtro por nó que falhou (etapa 1)."),
                                "message", string("Filtro por trecho da mensagem de erro (etapa 1)."),
                                "createdAfter", dateTime("Filtro: criados a partir de (etapa 1)."),
                                "createdBefore", dateTime("Filtro: criados antes de (etapa 1)."),
                                "confirmationToken", string("Token devolvido pelo preview (etapa 2)."))),
                        false, true, false, true, this::retryIncidents)
        ).stream().map(this::withBatchLimitInDescription).toList();
    }

    private KikwiTool withBatchLimitInDescription(KikwiTool tool) {
        if (!tool.description().contains("%d")) {
            return tool;
        }
        return new KikwiTool(tool.name(), tool.title(), tool.description().replace("%d", Integer.toString(maxBatchRetry)),
                tool.inputSchema(), tool.readOnly(), tool.destructive(), tool.idempotent(), tool.write(), tool.handler());
    }

    // ----------------------------------------------------------------------------------------------------------
    // start_process_instance
    // ----------------------------------------------------------------------------------------------------------

    private ToolResult startProcessInstance(ToolArgs args, McpCallContext context) {
        String key = args.requiredString("key");
        String businessKey = args.requiredString("businessKey");
        boolean dryRun = args.bool("dryRun", false);
        ProcessDefinition definition = definitions.byKey(key, null);

        Map<String, ProcessVariable> startVariables = new LinkedHashMap<>();
        args.object("variables").forEach((name, value) -> startVariables.put(name, new ProcessVariable(name, false, value)));

        List<VariableValidationError> violations = validateStartContract(definition, startVariables);
        if (!violations.isEmpty()) {
            throw new ToolException(ToolException.CONTRACT_VIOLATION,
                    violations.size() + " variável(is) fora do contrato de início de `" + key
                            + "`. Corrija e tente de novo (veja get_start_form).", violations);
        }

        String scopedTenant = TenantGuard.scopedTenant(context.identity());
        String tenant = scopedTenant != null ? scopedTenant : args.string("tenant");

        Optional<ProcessInstanceSummary> duplicate = findActiveDuplicate(key, businessKey, scopedTenant);
        if (duplicate.isPresent() && !args.bool("allowDuplicate", false)) {
            ProcessInstanceSummary existing = duplicate.get();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("existingProcessInstanceId", existing.id());
            putIfPresent(details, "monitorUrl", links.instance(context, existing.processDefinitionId(), existing.id()));
            throw new ToolException(ToolException.CONFLICT, "Já existe instância ATIVA de `" + key
                    + "` com businessKey `" + businessKey + "`. Confirme com o usuário antes de usar allowDuplicate=true.", details);
        }

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("key", key);
        plan.put("version", definition.version());
        plan.put("businessKey", businessKey);
        putIfPresent(plan, "tenant", tenant);
        plan.put("variables", startVariables.keySet());
        if (dryRun) {
            plan.put("valid", true);
            return ToolResult.of("Validação ok: `" + key + "` v" + definition.version()
                    + " pode ser iniciado com estas variáveis (nada foi iniciado).", plan);
        }

        ProcessInstance instance = processStartService.start(new ProcessInstanceStartRequest(
                key, null, ORIGIN, tenant, businessKey, startVariables, args.decimal("businessValue")),
                context.identity(), ORIGIN);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", instance.id());
        result.put("status", instance.status());
        result.put("businessKey", instance.businessKey());
        result.put("activeNodes", instance.activeNodes());
        result.put("startedBy", context.identity().actorId());
        putIfPresent(result, "monitorUrl", links.instance(context, instance.processDefinitionId(), instance.id()));
        return ToolResult.of("Instância `" + instance.id() + "` iniciada (" + instance.status() + ").", result);
    }

    private List<VariableValidationError> validateStartContract(ProcessDefinition definition, Map<String, ProcessVariable> startVariables) {
        FlowNodeDefinition startNode = definition.flowNodes() != null ? definition.flowNodes().get(definition.defaultStartPoint()) : null;
        if (startNode instanceof VariableBindingAware aware) {
            return contractValidator.validate(aware.variableBindingsOrEmpty().inputs(), definition, startVariables);
        }
        return List.of();
    }

    private Optional<ProcessInstanceSummary> findActiveDuplicate(String key, String businessKey, String scopedTenant) {
        ProcessInstanceSearchRequest request = new ProcessInstanceSearchRequest(
                null, null, List.of(key), null, null, scopedTenant, null,
                List.of(ProcessInstanceStatus.ACTIVE), businessKey, null, null, null, null, null,
                null, null, 0, 1);
        PageResult<ProcessInstanceSummary> page = ProcessInstanceQueryMapper
                .applyRequest(queryRepository.createProcessInstanceQuery(), request).listSummary();
        return page.content().stream().findFirst();
    }

    // ----------------------------------------------------------------------------------------------------------
    // retry_incidents
    // ----------------------------------------------------------------------------------------------------------

    private ToolResult retryIncidents(ToolArgs args, McpCallContext context) {
        String token = args.string("confirmationToken");
        return token != null ? executeRetry(token, context) : previewRetry(args, context);
    }

    private ToolResult previewRetry(ToolArgs args, McpCallContext context) {
        List<String> ids = args.strings("incidentIds");
        String key = args.string("processDefinitionKey");
        String nodeId = args.string("nodeId");
        String message = args.string("message");
        boolean hasFilter = key != null || nodeId != null || message != null
                || args.string("createdAfter") != null || args.string("createdBefore") != null;
        if (!ids.isEmpty() && hasFilter) {
            throw ToolException.invalidArgument("Informe `incidentIds` OU filtros, não os dois.");
        }
        if (ids.isEmpty() && !hasFilter) {
            throw ToolException.invalidArgument("Informe `incidentIds` ou ao menos um filtro (processDefinitionKey, nodeId, "
                    + "message, createdAfter/createdBefore). Para executar um preview já feito, passe `confirmationToken`.");
        }

        IncidentSearchRequest filter = hasFilter ? new IncidentSearchRequest(
                null, key != null ? definitions.versionIdsOf(key) : null,
                TenantGuard.scopedTenant(context.identity()), null, nodeId, null,
                List.of(IncidentStatus.OPEN), null, message,
                args.instant("createdAfter"), args.instant("createdBefore"),
                null, null, null, null) : null;
        List<String> candidates = incidentRetryService.resolveIds(
                new IncidentRetryRequest(hasFilter ? null : ids, filter), maxBatchRetry);

        List<Incident> eligible = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (String id : candidates.stream().distinct().toList()) {
            Optional<Incident> incident = queryRepository.findIncidentById(id)
                    .filter(found -> hasAccess(found, context));
            if (incident.isEmpty()) {
                skipped.add(Map.of("incidentId", id, "reason", "NOT_FOUND"));
            } else if (incident.get().status() != IncidentStatus.OPEN) {
                skipped.add(Map.of("incidentId", id, "reason", "NOT_OPEN (" + incident.get().status() + ")"));
            } else {
                eligible.add(incident.get());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", false);
        result.put("eligibleCount", eligible.size());
        result.put("byCause", eligible.stream().collect(Collectors.groupingBy(
                incident -> incident.taskDefinitionId() + ": " + variables.truncate(IncidentTools.firstLine(incident.message())),
                LinkedHashMap::new, Collectors.counting())));
        result.put("incidents", eligible.stream().map(incident -> IncidentTools.incidentRow(incident, variables, links, context)).toList());
        if (!skipped.isEmpty()) {
            result.put("skipped", skipped);
        }
        if (eligible.isEmpty()) {
            return ToolResult.of("Nenhum incidente aberto elegível para reprocessar; nenhum token emitido.", result);
        }

        ConfirmationTokenService.Issued issued = confirmationTokens.issue(RETRY_TOOL,
                eligible.stream().map(Incident::id).toList(), context.identity());
        result.put("confirmationToken", issued.token());
        result.put("expiresAt", issued.expiresAt());
        result.put("nextStep", "Mostre a lista ao usuário. Se ele confirmar, chame retry_incidents apenas com confirmationToken.");
        return ToolResult.of("PREVIEW (nada executado): " + eligible.size()
                + " incidente(s) seriam reprocessados. Peça confirmação ao usuário.", result);
    }

    private ToolResult executeRetry(String token, McpCallContext context) {
        List<String> ids = confirmationTokens.verify(token, RETRY_TOOL, context.identity());
        IncidentRetryResponse response = incidentRetryService.retryAll(ids, context.identity());

        List<Map<String, Object>> results = new ArrayList<>();
        for (IncidentRetryResult item : response.results()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("incidentId", item.incidentId());
            row.put("status", item.status());
            putIfPresent(row, "code", item.code());
            putIfPresent(row, "message", item.message());
            results.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", true);
        result.put("retriedCount", response.retriedCount());
        result.put("failedCount", response.failedCount());
        result.put("results", results);
        result.put("note", "Reprocessar recoloca a tarefa na fila; acompanhe com get_process_instance ou search_incidents "
                + "— se a causa persistir, um novo incidente será aberto.");
        return ToolResult.of(response.retriedCount() + " incidente(s) reprocessado(s), " + response.failedCount()
                + " falha(s).", result);
    }

    private boolean hasAccess(Incident incident, McpCallContext context) {
        String scoped = TenantGuard.scopedTenant(context.identity());
        if (scoped == null) {
            return true;
        }
        return queryRepository.findProcessInstanceById(incident.processInstanceId())
                .map(instance -> scoped.equals(instance.tenantId()))
                .orElse(false);
    }
}
