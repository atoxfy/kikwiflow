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
import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.mcp.ToolResult;
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.support.TenantGuard;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.form.FormDescriptor;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import io.kikwiflow.variable.FormDescriptorFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.kikwiflow.mcp.Schema.integer;
import static io.kikwiflow.mcp.Schema.object;
import static io.kikwiflow.mcp.Schema.props;
import static io.kikwiflow.mcp.Schema.string;
import static io.kikwiflow.mcp.support.NodeDescriber.putIfPresent;

/** Fila de tarefas humanas/externas: o que está esperando, com quem, há quanto tempo e o que falta preencher. */
public class ExternalTaskTools {

    static final int MAX_RESULTS = 50;

    private final QueryRepository queryRepository;
    private final VariableSecurityPolicyManager securityPolicyManager;
    private final DefinitionLookup definitions;
    private final MonitorLinks links;
    private final Clock clock;

    public ExternalTaskTools(QueryRepository queryRepository, VariableSecurityPolicyManager securityPolicyManager,
                             MonitorLinks links, Clock clock) {
        this.queryRepository = queryRepository;
        this.securityPolicyManager = securityPolicyManager;
        this.definitions = new DefinitionLookup(queryRepository);
        this.links = links;
        this.clock = clock;
    }

    public List<KikwiTool> tools() {
        return List.of(
                KikwiTool.read("search_external_tasks", "Buscar tarefas humanas/externas",
                        "Lista tarefas aguardando ação humana ou de sistema externo (as mais antigas primeiro), com "
                                + "responsável e idade. Use para \"o que está parado esperando alguém\" e para achar "
                                + "gargalos de aprovação.",
                        object(props(
                                "processDefinitionKey", string("Chave do processo (todas as versões)."),
                                "nodeId", string("Id do nó (taskDefinitionId)."),
                                "assignee", string("Responsável atual."),
                                "processInstanceId", string("Só tarefas desta instância."),
                                "limit", integer("Máximo de tarefas (padrão 20).", 1, MAX_RESULTS))),
                        this::searchExternalTasks),
                KikwiTool.read("get_external_task_form", "Formulário de uma tarefa",
                        "Mostra o formulário de uma tarefa humana/externa: campos esperados, formatos, obrigatórios e os "
                                + "valores atuais da instância — o que ainda falta para concluir.",
                        object(props("externalTaskId", string("Id da tarefa externa.")), "externalTaskId"),
                        this::getExternalTaskForm));
    }

    private ToolResult searchExternalTasks(ToolArgs args, McpCallContext context) {
        String key = args.string("processDefinitionKey");
        String nodeId = args.string("nodeId");
        int limit = args.integer("limit", 20, 1, MAX_RESULTS);
        String tenant = TenantGuard.scopedTenant(context.identity());

        List<String> definitionIds = key != null ? definitions.versionIdsOf(key) : Collections.singletonList(null);
        List<ExternalTask> found = new ArrayList<>();
        for (String definitionId : definitionIds) {
            ExternalTaskQuery query = queryRepository.createExternalTaskQuery();
            if (definitionId != null) {
                query.processDefinitionId(definitionId);
            }
            if (nodeId != null) {
                query.taskDefinitionId(nodeId);
            }
            if (tenant != null) {
                query.tenantId(tenant);
            }
            String assignee = args.string("assignee");
            if (assignee != null) {
                query.assignee(assignee);
            }
            String instanceId = args.string("processInstanceId");
            if (instanceId != null) {
                query.processInstanceId(instanceId);
            }
            found.addAll(query.list());
        }

        List<ExternalTask> sorted = found.stream()
                .sorted(Comparator.comparing(ExternalTask::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExternalTask task : sorted.subList(0, Math.min(limit, sorted.size()))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("externalTaskId", task.id());
            row.put("nodeId", task.taskDefinitionId());
            putIfPresent(row, "name", task.displayName() != null ? task.displayName() : task.name());
            putIfPresent(row, "type", task.type());
            putIfPresent(row, "status", task.status());
            putIfPresent(row, "assignee", task.assignee());
            row.put("processInstanceId", task.processInstanceId());
            putIfPresent(row, "createdAt", task.createdAt());
            if (task.createdAt() != null) {
                row.put("waitingHours", Duration.between(task.createdAt(), clock.instant()).toHours());
            }
            putIfPresent(row, "monitorUrl", links.instance(context, task.processDefinitionId(), task.processInstanceId()));
            rows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", sorted.size());
        result.put("tasks", rows);
        return ToolResult.of(sorted.size() + " tarefa(s) aguardando" + (sorted.size() > limit ? "; mostrando as " + limit + " mais antigas." : "."), result);
    }

    private ToolResult getExternalTaskForm(ToolArgs args, McpCallContext context) {
        String id = args.requiredString("externalTaskId");
        ExternalTask task = queryRepository.findExternalTaskById(id)
                .orElseThrow(() -> ToolException.notFound("Tarefa externa `" + id + "` não encontrada (talvez já concluída)."));
        TenantGuard.checkAccess(context.identity(), task.tenantId(), "Tarefa externa `" + id + "` não encontrada.");
        ProcessDefinition definition = definitions.byId(task.processDefinitionId())
                .orElseThrow(() -> ToolException.notFound("Definição `" + task.processDefinitionId() + "` não encontrada."));
        ProcessInstance instance = queryRepository.findProcessInstanceById(task.processInstanceId())
                .orElseThrow(() -> ToolException.notFound("Instância `" + task.processInstanceId() + "` não encontrada."));

        // Os valores atuais pré-preenchem o formulário: passam pela mesma política de leitura das demais ferramentas.
        Map<String, ProcessVariable> visible = instance.variables() == null ? Map.of()
                : securityPolicyManager.applyReadPoliciesAndMasking(definition.id(), context.identity(), instance.variables());
        FormDescriptor form = FormDescriptorFactory.forNode(definition, task.taskDefinitionId(),
                definition.flowNodes().get(task.taskDefinitionId()), visible);
        long missing = form.fields() == null ? 0 : form.fields().stream()
                .filter(field -> field.required() && (field.value() == null || field.value().toString().isBlank()))
                .count();
        return ToolResult.of("Formulário de `" + task.taskDefinitionId() + "`: " + missing + " campo(s) obrigatório(s) sem valor.", form);
    }
}
