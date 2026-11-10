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

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.mcp.KikwiTool;
import io.kikwiflow.mcp.McpCallContext;
import io.kikwiflow.mcp.ToolArgs;
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.NodeDescriber;
import io.kikwiflow.mcp.support.VariablePresenter;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindingAware;
import io.kikwiflow.model.execution.AgentActivityReport;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.kikwiflow.mcp.Schema.bool;
import static io.kikwiflow.mcp.Schema.freeObject;
import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/**
 * Ferramentas de worker: um agente fora do host (um harness como Claude Code, Codex ou o Tucano) pega missões de
 * {@code AGENT_TASK} pelo MCP e as conclui com o mesmo protocolo de lease do REST (docs/engine/31, E2): listar →
 * assumir → trabalhar → concluir, falhar ou lançar um erro de negócio. O motor continua dono do contrato (422), da
 * auditoria ({@code AGENT_*}) e do caminho do processo.
 *
 * <p>Só registradas com {@code kikwiflow.mcp.worker-tools.enabled=true}. O worker só enxerga os tópicos de
 * {@link Options#topics()} e só missões do tenant da própria identidade; o {@code workerId} é
 * {@value #WORKER_PREFIX}{@code <actorId>}, então cada identidade (token) é um worker. Toda ferramenta de escrita
 * renova o lease.
 */
public class WorkerTools {

    public static final String WORKER_PREFIX = "mcp:";
    static final String LIST_TOOL = "list_available_missions";
    static final String GET_TOOL = "get_mission";
    static final String CLAIM_TOOL = "claim_mission";
    static final String ACTIVITY_TOOL = "report_mission_activity";
    static final String COMPLETE_TOOL = "complete_mission";
    static final String FAIL_TOOL = "fail_mission";
    static final String ERROR_TOOL = "throw_mission_error";

    private static final int MAX_LIST = 50;
    private static final int LIST_OBJECTIVE_CHARS = 600;
    private static final int MAX_SUMMARY_CHARS = 2000;
    private static final int DEFAULT_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    /**
     * @param topics        tópicos de {@code AGENT_TASK} que o worker MCP pode pegar (obrigatório: sem tópico, nenhum)
     * @param leaseDuration quanto vale o lease a cada assumir/renovar
     */
    public record Options(boolean enabled, List<String> topics, Duration leaseDuration) {

        public Options {
            topics = topics == null ? List.of() : topics.stream().filter(t -> t != null && !t.isBlank()).toList();
            leaseDuration = leaseDuration == null ? Duration.ofMinutes(30) : leaseDuration;
        }

        public static Options disabled() {
            return new Options(false, List.of(), null);
        }
    }

    private final KikwiflowEngine engine;
    private final QueryRepository queryRepository;
    private final DefinitionLookup definitions;
    private final VariablePresenter variables;
    private final MonitorLinks links;
    private final Options options;
    private final Clock clock;

    public WorkerTools(KikwiflowEngine engine, QueryRepository queryRepository, VariablePresenter variables,
                       MonitorLinks links, Options options, Clock clock) {
        this.engine = engine;
        this.queryRepository = queryRepository;
        this.definitions = new DefinitionLookup(queryRepository);
        this.variables = variables;
        this.links = links;
        this.options = options;
        this.clock = clock;
    }

    public List<KikwiTool> tools() {
        String topics = String.join(", ", options.topics());
        return List.of(
                KikwiTool.read(LIST_TOOL, "Missões disponíveis",
                        "Lista as missões de agente esperando um worker no tópico (as mais antigas primeiro), sem "
                                + "assumir nenhuma. Cada item tem `id`, `title` e o começo do objetivo. Para trabalhar uma, "
                                + "chame claim_mission com o id. Tópicos deste servidor: " + topics + ".",
                        object(props(
                                "topic", string("Tópico das missões (um de: " + topics + ")."),
                                "limit", integer("Máximo de missões (padrão 20).", 1, MAX_LIST)), "topic"),
                        this::listAvailable),
                KikwiTool.read(GET_TOOL, "Detalhe da missão",
                        "Objetivo completo, dados de entrada, contrato de saída (cada campo com formato, opções e se é "
                                + "obrigatório), limites e quem tem o lease agora.",
                        object(props("missionId", string("Id da missão.")), "missionId"),
                        this::getMission),
                new KikwiTool(CLAIM_TOOL, "Assumir missão",
                        "Assume a missão para este worker (lease de " + options.leaseDuration().toMinutes()
                                + " min, renovado a cada chamada de escrita) e devolve o objetivo, os dados de entrada e o "
                                + "contrato de saída. Se outro worker já a assumiu, responde CONFLICT: deixe-a. Depois "
                                + "de assumir, trabalhe e termine com complete_mission, fail_mission ou throw_mission_error.",
                        object(props("missionId", string("Id da missão (o `id` de list_available_missions).")), "missionId"),
                        false, false, true, true, this::claim),
                new KikwiTool(ACTIVITY_TOOL, "Registrar andamento",
                        "Registra no histórico da missão o que você fez (ex.: ferramenta usada, arquivo gerado) e renova "
                                + "o lease. Use em trabalhos longos, a cada etapa. `type`: TOOL_INVOKED (padrão) ou "
                                + "HANDOFF (você precisa de uma pessoa; registre antes de throw_mission_error).",
                        object(props(
                                "missionId", string("Id da missão."),
                                "summary", string("O que foi feito, em uma ou duas frases."),
                                "type", string("TOOL_INVOKED ou HANDOFF.")), "missionId", "summary"),
                        false, false, false, true, this::reportActivity),
                new KikwiTool(COMPLETE_TOOL, "Concluir missão",
                        "Conclui a missão com as saídas do contrato (veja `outputs` em claim_mission). O motor valida: "
                                + "fora do contrato responde CONTRACT_VIOLATION com a lista do que corrigir, e a missão "
                                + "continua sua; corrija e chame de novo. Concluída, o processo segue.",
                        object(props(
                                "missionId", string("Id da missão."),
                                "outputs", freeObject("Saídas do contrato, nome → valor, ex.: {\"categoria\": \"FISCAL\"}."),
                                "summary", string("Resumo do que foi feito (vai para o histórico).")), "missionId", "outputs"),
                        false, false, false, true, this::complete),
                new KikwiTool(FAIL_TOOL, "Falhar missão",
                        "Falha técnica: você não conseguiu trabalhar a missão (ferramenta fora do ar, acesso negado). "
                                + "Com `retryable=true` (padrão) ela volta à fila em alguns minutos enquanto houver "
                                + "tentativas; com `false`, vira incidente no Monitor para uma pessoa olhar.",
                        object(props(
                                "missionId", string("Id da missão."),
                                "reason", string("O que deu errado."),
                                "retryable", bool("Tentar de novo depois (padrão true).")), "missionId", "reason"),
                        false, false, false, true, this::fail),
                new KikwiTool(ERROR_TOOL, "Erro de negócio",
                        "A missão não pode ser cumprida por uma regra de negócio (ex.: documento ilegível, cliente "
                                + "desistiu, precisa de uma pessoa). O processo segue pelo tratador de erro do nó com este "
                                + "`errorCode`; sem tratador, vira incidente. Use os códigos que o objetivo da missão citar.",
                        object(props(
                                "missionId", string("Id da missão."),
                                "errorCode", string("Código do erro de negócio."),
                                "message", string("Explicação para quem for ver.")), "missionId", "errorCode"),
                        false, false, false, true, this::throwError));
    }

    // ----------------------------------------------------------------------------------------------------------
    // leitura
    // ----------------------------------------------------------------------------------------------------------

    private ToolResult listAvailable(ToolArgs args, McpCallContext context) {
        String topic = allowedTopic(args.requiredString("topic"));
        int limit = args.integer("limit", 20, 1, MAX_LIST);
        String tenant = tenantOf(context);
        ExternalTaskQuery query = queryRepository.createExternalTaskQuery()
                .topicName(topic).type(ExternalTaskType.AGENT).fetchableAt(clock.instant());
        if (tenant != null) {
            query.tenantId(tenant);
        }
        List<ExternalTask> available = query.list().stream()
                .filter(task -> Objects.equals(task.tenantId(), tenant))
                .sorted(Comparator.comparing(ExternalTask::createdAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(limit)
                .toList();

        List<Map<String, Object>> missions = new ArrayList<>();
        for (ExternalTask task : available) {
            Optional<ProcessDefinition> definition = definitions.byId(task.processDefinitionId());
            AgentTaskDefinition node = definition.map(d -> agentNode(d, task)).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", task.id());
            row.put("title", title(task, definition.orElse(null), node));
            row.put("topic", task.topicName());
            definition.ifPresent(d -> row.put("process", d.key()));
            row.put("node", task.taskDefinitionId());
            if (node != null && node.agentRef() != null) {
                putIfPresent(row, "agent", node.agentRef().key());
            }
            putIfPresent(row, "createdAt", task.createdAt());
            putIfPresent(row, "objective", variables.truncate(cut(objective(node), LIST_OBJECTIVE_CHARS)));
            missions.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("missions", missions);
        return ToolResult.of(missions.isEmpty() ? "Nenhuma missão esperando no tópico `" + topic + "`."
                : missions.size() + " missão(ões) esperando no tópico `" + topic + "`. Assuma uma com claim_mission.", result);
    }

    private ToolResult getMission(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        return ToolResult.of("Missão `" + task.id() + "`.", view(task, null, context));
    }

    // ----------------------------------------------------------------------------------------------------------
    // escrita
    // ----------------------------------------------------------------------------------------------------------

    private ToolResult claim(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        String workerId = workerId(context);
        boolean alreadyMine = task.isLockedBy(workerId, clock.instant());
        LockedExternalTask locked = engine.lockExternalTask(task.id(), workerId, options.leaseDuration(), context.identity());
        if (!alreadyMine) {
            engine.reportActivities(task.id(), workerId, List.of(new AgentActivityReport(
                    "AGENT_MISSION_STARTED", clock.instant(), Map.of("runtime", "mcp", "worker", workerId))),
                    context.identity());
        }
        return ToolResult.of((alreadyMine ? "A missão já era sua; lease renovado. " : "Missão assumida. ")
                + "Trabalhe o objetivo e conclua com complete_mission (saídas em `outputs`).",
                view(locked.task(), locked, context));
    }

    private ToolResult reportActivity(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        String workerId = workerId(context);
        String type = Optional.ofNullable(args.string("type")).orElse("TOOL_INVOKED").trim().toUpperCase();
        if (!type.equals("TOOL_INVOKED") && !type.equals("HANDOFF")) {
            throw ToolException.invalidArgument("`type` deve ser TOOL_INVOKED ou HANDOFF.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runtime", "mcp");
        data.put("summary", cut(args.requiredString("summary"), MAX_SUMMARY_CHARS));
        if (type.equals("HANDOFF")) {
            data.put("toHuman", true);
        }
        engine.extendLock(task.id(), workerId, options.leaseDuration(), context.identity());
        engine.reportActivities(task.id(), workerId, List.of(new AgentActivityReport("AGENT_" + type,
                clock.instant(), data)), context.identity());
        return ToolResult.of("Andamento registrado; lease renovado.", Map.of("missionId", task.id(),
                "leaseExpiresAt", clock.instant().plus(options.leaseDuration())));
    }

    private ToolResult complete(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        Map<String, ProcessVariable> outputs = new LinkedHashMap<>();
        args.object("outputs").forEach((name, value) -> outputs.put(name, new ProcessVariable(name, false, value)));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runtime", "mcp");
        putIfPresent(report, "summary", cut(args.string("summary"), MAX_SUMMARY_CHARS));
        ProcessInstance instance = engine.completeExternalTask(task.id(), workerId(context), outputs, report,
                context.identity());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("missionId", task.id());
        result.put("completed", true);
        result.put("processInstanceId", instance.id());
        putIfPresent(result, "processStatus", instance.status());
        putIfPresent(result, "activeNodes", instance.activeNodes());
        putIfPresent(result, "monitorUrl", links.instance(context, instance.processDefinitionId(), instance.id()));
        return ToolResult.of("Missão concluída; o processo seguiu.", result);
    }

    private ToolResult fail(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        boolean retryable = args.bool("retryable", true);
        int remaining = !retryable ? 0 : Math.max(0, (task.retries() == null ? DEFAULT_RETRIES + 1 : task.retries()) - 1);
        engine.handleFailure(task.id(), workerId(context), cut(args.requiredString("reason"), MAX_SUMMARY_CHARS), null,
                remaining, remaining > 0 ? RETRY_DELAY : null, context.identity());
        return ToolResult.of(remaining > 0
                        ? "Falha registrada; a missão volta à fila em " + RETRY_DELAY.toMinutes() + " min (" + remaining
                        + " tentativa(s) restante(s))."
                        : "Falha registrada sem nova tentativa: a missão virou incidente no Monitor.",
                Map.of("missionId", task.id(), "retriesLeft", remaining));
    }

    private ToolResult throwError(ToolArgs args, McpCallContext context) {
        ExternalTask task = mission(args.requiredString("missionId"), context);
        ProcessInstance instance = engine.throwBusinessError(task.id(), workerId(context),
                args.requiredString("errorCode"), cut(args.string("message"), MAX_SUMMARY_CHARS), Map.of(),
                context.identity());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("missionId", task.id());
        result.put("processInstanceId", instance.id());
        putIfPresent(result, "processStatus", instance.status());
        putIfPresent(result, "activeNodes", instance.activeNodes());
        return ToolResult.of("Erro de negócio registrado; o processo seguiu pelo tratador do nó (ou abriu incidente "
                + "se não há tratador).", result);
    }

    // ----------------------------------------------------------------------------------------------------------
    // apoio
    // ----------------------------------------------------------------------------------------------------------

    /** A missão do tenant de quem chama, num tópico permitido; o resto não existe para este worker. */
    private ExternalTask mission(String missionId, McpCallContext context) {
        String tenant = tenantOf(context);
        ExternalTask task = queryRepository.findExternalTaskById(missionId)
                .filter(t -> t.type() == ExternalTaskType.AGENT)
                .filter(t -> Objects.equals(t.tenantId(), tenant))
                .filter(t -> options.topics().contains(t.topicName()))
                .orElseThrow(() -> ToolException.notFound("Missão `" + missionId + "` não existe para este worker "
                        + "(concluída, interrompida pelo processo, ou fora dos tópicos dele)."));
        return task;
    }

    private String allowedTopic(String topic) {
        if (!options.topics().contains(topic)) {
            throw new ToolException(ToolException.FORBIDDEN, "Tópico `" + topic + "` não liberado para workers MCP neste "
                    + "servidor. Tópicos liberados: " + String.join(", ", options.topics()) + ".");
        }
        return topic;
    }

    /** O tenant de quem chama. Worker anônimo nunca: a identidade é quem responde pelo que a missão fez. */
    private static String tenantOf(McpCallContext context) {
        if (context.isAnonymous()) {
            throw new ToolException(ToolException.FORBIDDEN, "Ferramentas de worker exigem uma identidade autenticada "
                    + "(o token do worker); esta conexão MCP está anônima.");
        }
        String tenant = context.identity().tenantId();
        return tenant == null || tenant.isBlank() ? null : tenant;
    }

    static String workerId(McpCallContext context) {
        IdentityContext identity = context.identity();
        return WORKER_PREFIX + identity.actorId();
    }

    private Map<String, Object> view(ExternalTask task, LockedExternalTask locked, McpCallContext context) {
        Optional<ProcessDefinition> definition = definitions.byId(task.processDefinitionId());
        AgentTaskDefinition node = definition.map(d -> agentNode(d, task)).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", task.id());
        view.put("title", title(task, definition.orElse(null), node));
        view.put("topic", task.topicName());

        Map<String, Object> process = new LinkedHashMap<>();
        definition.ifPresent(d -> {
            process.put("key", d.key());
            putIfPresent(process, "version", d.version());
        });
        process.put("processInstanceId", task.processInstanceId());
        Optional<ProcessInstance> instance = queryRepository.findProcessInstanceById(task.processInstanceId());
        instance.ifPresent(i -> putIfPresent(process, "businessKey", i.businessKey()));
        view.put("process", process);
        view.put("node", task.taskDefinitionId());
        if (node != null) {
            if (node.agentRef() != null) {
                Map<String, Object> agent = new LinkedHashMap<>();
                agent.put("key", node.agentRef().key());
                putIfPresent(agent, "version", node.agentRef().version());
                view.put("agent", agent);
            }
            putIfPresent(view, "objective", objective(node));
            if (!node.tools().isEmpty()) {
                view.put("toolHints", node.tools());
            }
            if (node.limits() != null) {
                Map<String, Object> limits = new LinkedHashMap<>();
                putIfPresent(limits, "maxTurns", node.limits().maxTurns());
                putIfPresent(limits, "ttl", node.limits().ttl());
                if (!limits.isEmpty()) {
                    view.put("limits", limits);
                }
            }
        }

        Map<String, ProcessVariable> inputs = locked != null ? locked.variables() : inputsOf(task, node, instance);
        view.put("inputs", variables.present(task.processDefinitionId(), context.identity(), inputs));
        view.put("outputs", definition.map(d -> outputs(d, node)).orElse(List.of()));

        Map<String, Object> lease = new LinkedHashMap<>();
        boolean mine = task.isLockedBy(workerId(context), clock.instant());
        lease.put("yours", mine);
        if (task.lockOwner() != null && task.lockExpiresAt() != null && task.lockExpiresAt().isAfter(clock.instant())) {
            lease.put("owner", task.lockOwner());
            lease.put("expiresAt", task.lockExpiresAt());
        }
        view.put("lease", lease);
        putIfPresent(view, "createdAt", task.createdAt());
        putIfPresent(view, "monitorUrl", links.instance(context, task.processDefinitionId(), task.processInstanceId()));
        return view;
    }

    private static Map<String, ProcessVariable> inputsOf(ExternalTask task, FlowNodeDefinition node,
                                                         Optional<ProcessInstance> instance) {
        Map<String, ProcessVariable> all = instance.map(ProcessInstance::variables).orElse(null);
        if (all == null) {
            return Map.of();
        }
        if (node instanceof VariableBindingAware aware && !aware.variableBindingsOrEmpty().inputs().isEmpty()) {
            Map<String, ProcessVariable> inputs = new LinkedHashMap<>();
            aware.variableBindingsOrEmpty().inputs().forEach(b -> {
                if (all.containsKey(b.variable())) {
                    inputs.put(b.variable(), all.get(b.variable()));
                }
            });
            return inputs;
        }
        return all;
    }

    private static List<Map<String, Object>> outputs(ProcessDefinition definition, AgentTaskDefinition node) {
        if (node == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (VariableBinding binding : node.variableBindingsOrEmpty().outputs()) {
            Map<String, Object> field = definition.findVariableDeclaration(binding.variable())
                    .map(NodeDescriber::declaration).map(LinkedHashMap::new).orElseGet(LinkedHashMap::new);
            field.put("key", binding.variable());
            field.put("required", binding.requiresValue());
            result.add(field);
        }
        return result;
    }

    private static AgentTaskDefinition agentNode(ProcessDefinition definition, ExternalTask task) {
        FlowNodeDefinition node = definition.flowNodes() == null ? null : definition.flowNodes().get(task.taskDefinitionId());
        return node instanceof AgentTaskDefinition agent ? agent : null;
    }

    private static String title(ExternalTask task, ProcessDefinition definition, AgentTaskDefinition node) {
        String step = node != null && node.name() != null ? node.name()
                : task.name() != null ? task.name() : task.taskDefinitionId();
        String process = definition == null ? null : definition.name() != null ? definition.name() : definition.key();
        return process == null ? step : step + " · " + process;
    }

    private static String objective(AgentTaskDefinition node) {
        return node == null || node.extensionProperties() == null ? null
                : node.extensionProperties().get(NodeDescriber.PROMPT_KEY);
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
