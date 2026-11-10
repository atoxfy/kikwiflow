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
package io.kikwiflow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.exception.ExternalTaskLockException;
import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.management.service.ProcessInstanceSnapshotService;
import io.kikwiflow.mcp.support.ConfirmationTokenService;
import io.kikwiflow.mcp.tools.WorkerTools;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.AgentTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.definition.process.variable.VariableOption;
import io.kikwiflow.model.execution.AgentActivityReport;
import io.kikwiflow.model.execution.LockedExternalTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExternalTaskType;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.security.DefaultVariableSecurityPolicyManager;
import io.kikwiflow.variable.VariableValidationError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Ferramentas de worker do MCP (um agente fora do host pega missões)")
class WorkerToolsTest {

    private static final String TOPIC = "tucano";
    private static final String TENANT = "org";
    private static final IdentityContext WORKER = new IdentityContext("worker:notebook", TENANT);
    private static final Duration LEASE = Duration.ofMinutes(30);

    private QueryRepository queryRepository;
    private KikwiflowEngine engine;
    private ExternalTaskQuery taskQuery;
    private KikwiMcpServerFactory factory;
    private Map<String, KikwiTool> tools;
    private ExternalTask mission;

    @BeforeEach
    void setUp() {
        queryRepository = mock(QueryRepository.class);
        engine = mock(KikwiflowEngine.class);
        taskQuery = mock(ExternalTaskQuery.class, RETURNS_SELF);
        when(queryRepository.createExternalTaskQuery()).thenReturn(taskQuery);
        when(queryRepository.findProcessDefinitionById("def-1")).thenReturn(Optional.of(definition()));
        when(queryRepository.findProcessInstanceById("pi-1")).thenReturn(Optional.of(instance()));

        mission = task("m-1", TENANT, TOPIC);
        when(queryRepository.findExternalTaskById("m-1")).thenReturn(Optional.of(mission));
        when(taskQuery.list()).thenReturn(List.of(mission, task("m-2", "outra-org", TOPIC)));

        factory = new KikwiMcpServerFactory(new ObjectMapper().findAndRegisterModules(), true);
        tools = catalog(new WorkerTools.Options(true, List.of(TOPIC), LEASE));
    }

    private Map<String, KikwiTool> catalog(WorkerTools.Options worker) {
        Map<String, KikwiTool> byName = new LinkedHashMap<>();
        KikwiMcpCatalog.tools(engine, queryRepository, mock(ProcessInstanceSnapshotService.class),
                        new DefaultVariableSecurityPolicyManager(), new ConfirmationTokenService(
                                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5),
                                Clock.systemUTC()),
                        new KikwiMcpCatalog.Options(false, 20, 2000, null), worker, Clock.systemUTC())
                .forEach(tool -> byName.put(tool.name(), tool));
        return byName;
    }

    @Test
    @DisplayName("Sem a flag, nenhuma ferramenta de worker; com ela, listar é leitura e o resto é escrita")
    void catalogo() {
        assertFalse(catalog(WorkerTools.Options.disabled()).containsKey("claim_mission"));

        assertTrue(tools.get("list_available_missions").readOnly());
        assertTrue(tools.get("get_mission").readOnly());
        for (String write : List.of("claim_mission", "report_mission_activity", "complete_mission", "fail_mission",
                "throw_mission_error")) {
            assertTrue(tools.get(write).write(), write);
            assertFalse(tools.get(write).destructive(), write + " não deve pedir aprovação por padrão");
        }
    }

    @Nested
    @DisplayName("Quando o worker lista as missões")
    class Listar {

        @Test
        @DisplayName("então vê só as do próprio tenant, no formato que um gatilho por item entende (id e title)")
        @SuppressWarnings("unchecked")
        void lista() {
            Map<String, Object> result = structured(call("list_available_missions", WORKER, Map.of("topic", TOPIC)));

            List<Map<String, Object>> missions = (List<Map<String, Object>>) result.get("missions");
            assertEquals(1, missions.size());
            assertEquals("m-1", missions.getFirst().get("id"));
            assertEquals("Classificar documento · Triagem", missions.getFirst().get("title"));
            assertEquals("Classifique o documento {{documento}}.", missions.getFirst().get("objective"));
            verify(taskQuery).topicName(TOPIC);
            verify(taskQuery).type(ExternalTaskType.AGENT);
            verify(taskQuery).tenantId(TENANT);
        }

        @Test
        @DisplayName("num tópico não liberado, então é recusado")
        void topicoNaoLiberado() {
            assertEquals("FORBIDDEN", errorCode(call("list_available_missions", WORKER, Map.of("topic", "kikwi.agents"))));
        }

        @Test
        @DisplayName("sem identidade, então é recusado mesmo para ler")
        void anonimo() {
            assertEquals("FORBIDDEN", errorCode(call("list_available_missions", null, Map.of("topic", TOPIC))));
        }
    }

    @Nested
    @DisplayName("Quando o worker assume e trabalha a missão")
    class Trabalhar {

        @BeforeEach
        void travavel() {
            when(engine.lockExternalTask(eq("m-1"), eq("mcp:worker:notebook"), eq(LEASE), any()))
                    .thenReturn(new LockedExternalTask(mission.toBuilder().lockOwner("mcp:worker:notebook")
                            .lockExpiresAt(Instant.now().plus(LEASE)).build(), "P-1",
                            Map.of("documento", new ProcessVariable("documento", false, "nota.pdf")),
                            definition().flowNodes().get("classificar"), List.of()));
        }

        @Test
        @DisplayName("então trava pelo id com o workerId da identidade, registra o início e devolve o contrato de saída")
        @SuppressWarnings("unchecked")
        void assume() {
            Map<String, Object> result = structured(call("claim_mission", WORKER, Map.of("missionId", "m-1")));

            ArgumentCaptor<List<AgentActivityReport>> started = ArgumentCaptor.forClass(List.class);
            verify(engine).reportActivities(eq("m-1"), eq("mcp:worker:notebook"), started.capture(), any());
            assertEquals("AGENT_MISSION_STARTED", started.getValue().getFirst().type());
            assertEquals(Map.of("documento", "nota.pdf"), result.get("inputs"));
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) result.get("outputs");
            assertEquals("categoria", outputs.getFirst().get("key"));
            assertEquals(true, outputs.getFirst().get("required"));
            assertEquals(2, ((List<?>) outputs.getFirst().get("options")).size());
            assertEquals("P-1", ((Map<String, Object>) result.get("process")).get("businessKey"));
        }

        @Test
        @DisplayName("e outro worker já a assumiu, então recebe CONFLICT")
        void conflito() {
            when(engine.lockExternalTask(eq("m-1"), any(), any(), any()))
                    .thenThrow(new ExternalTaskLockException("outro worker"));

            assertEquals("CONFLICT", errorCode(call("claim_mission", WORKER, Map.of("missionId", "m-1"))));
        }

        @Test
        @DisplayName("e a missão é de outro tenant, então ela não existe para ele")
        void outroTenant() {
            when(queryRepository.findExternalTaskById("m-2")).thenReturn(Optional.of(task("m-2", "outra-org", TOPIC)));

            assertEquals("NOT_FOUND", errorCode(call("claim_mission", WORKER, Map.of("missionId", "m-2"))));
            verify(engine, never()).lockExternalTask(any(), any(), any(), any());
        }

        @Test
        @DisplayName("e conclui fora do contrato, então recebe a lista do que corrigir; corrigindo, o processo segue")
        void conclui() {
            when(engine.completeExternalTask(eq("m-1"), eq("mcp:worker:notebook"), anyMap(), anyMap(), any()))
                    .thenThrow(new VariableValidationException("classificar",
                            List.of(new VariableValidationError("categoria", "INVALID_OPTION", "fora das opções"))))
                    .thenReturn(instance());

            assertEquals("CONTRACT_VIOLATION", errorCode(call("complete_mission", WORKER,
                    Map.of("missionId", "m-1", "outputs", Map.of("categoria", "NOTA")))));
            Map<String, Object> done = structured(call("complete_mission", WORKER,
                    Map.of("missionId", "m-1", "outputs", Map.of("categoria", "FISCAL"), "summary", "É uma nota fiscal.")));

            assertEquals(true, done.get("completed"));
            ArgumentCaptor<Map<String, Object>> report = ArgumentCaptor.forClass(Map.class);
            verify(engine, org.mockito.Mockito.times(2)).completeExternalTask(eq("m-1"), eq("mcp:worker:notebook"),
                    anyMap(), report.capture(), any());
            assertEquals("mcp", report.getValue().get("runtime"));
            assertEquals("É uma nota fiscal.", report.getValue().get("summary"));
        }

        @Test
        @DisplayName("e falha sem nova tentativa, então a missão vai para incidente (retries 0)")
        void falhaSemRetry() {
            structured(call("fail_mission", WORKER, Map.of("missionId", "m-1", "reason", "sem acesso ao drive",
                    "retryable", false)));

            verify(engine).handleFailure(eq("m-1"), eq("mcp:worker:notebook"), eq("sem acesso ao drive"), any(),
                    eq(0), eq(null), any());
        }

        @Test
        @DisplayName("e falha com nova tentativa, então gasta uma das tentativas restantes")
        void falhaComRetry() {
            structured(call("fail_mission", WORKER, Map.of("missionId", "m-1", "reason", "drive fora do ar")));

            verify(engine).handleFailure(eq("m-1"), any(), any(), any(), eq(2), eq(Duration.ofMinutes(5)), any());
        }

        @Test
        @DisplayName("e lança um erro de negócio, então o processo segue pelo tratador do nó")
        void erroDeNegocio() {
            when(engine.throwBusinessError(any(), any(), any(), any(), anyMap(), any())).thenReturn(instance());

            structured(call("throw_mission_error", WORKER, Map.of("missionId", "m-1", "errorCode", "ILEGIVEL")));

            verify(engine).throwBusinessError(eq("m-1"), eq("mcp:worker:notebook"), eq("ILEGIVEL"), any(), anyMap(), any());
        }

        @Test
        @DisplayName("e registra andamento, então renova o lease e grava a atividade")
        void andamento() {
            structured(call("report_mission_activity", WORKER, Map.of("missionId", "m-1", "summary", "baixei o PDF")));

            verify(engine).extendLock("m-1", "mcp:worker:notebook", LEASE, WORKER);
            verify(engine).reportActivities(eq("m-1"), eq("mcp:worker:notebook"), any(), any());
            verify(engine, never()).handleFailure(any(), any(), any(), any(), anyInt(), any(), any());
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private McpSchema.CallToolResult call(String tool, IdentityContext identity, Map<String, Object> arguments) {
        return factory.call(tools.get(tool), new McpCallContext(identity, "http://localhost:8081"), arguments);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(McpSchema.CallToolResult result) {
        assertFalse(Boolean.TRUE.equals(result.isError()), () -> "erro inesperado: " + result.content());
        return (Map<String, Object>) result.structuredContent();
    }

    @SuppressWarnings("unchecked")
    private static String errorCode(McpSchema.CallToolResult result) {
        assertTrue(result.isError());
        return (String) ((Map<String, Object>) ((Map<String, Object>) result.structuredContent()).get("error")).get("code");
    }

    private static ExternalTask task(String id, String tenant, String topic) {
        return ExternalTask.builder().id(id).tenantId(tenant).topicName(topic).type(ExternalTaskType.AGENT)
                .status(ExternalTaskStatus.CREATED).taskDefinitionId("classificar").processDefinitionId("def-1")
                .processInstanceId("pi-1").createdAt(Instant.now()).retries(3).build();
    }

    private static ProcessInstance instance() {
        return ProcessInstance.builder().id("pi-1").processDefinitionId("def-1").businessKey("P-1")
                .status(ProcessInstanceStatus.ACTIVE).startedAt(Instant.now()).tenantId(TENANT)
                .variables(Map.of("documento", new ProcessVariable("documento", false, "nota.pdf"),
                        "segredoDoOutroPasso", new ProcessVariable("segredoDoOutroPasso", false, "x")))
                .activeNodes(Map.of("classificar", 1))
                .build();
    }

    private static ProcessDefinition definition() {
        Map<String, FlowNodeDefinition> nodes = new LinkedHashMap<>();
        nodes.put("classificar", AgentTaskDefinition.builder().id("classificar").name("Classificar documento")
                .topic(TOPIC).agentRef(new AgentTaskDefinition.AgentRef("classificador", null))
                .extensionProperties(Map.of("kikwi:prompt", "Classifique o documento {{documento}}."))
                .variableBindings(new VariableBindings(List.of(new VariableBinding("documento", true)),
                        List.of(new VariableBinding("categoria", true))))
                .build());
        return ProcessDefinition.builder().id("def-1").key("triagem").name("Triagem").version(1)
                .defaultStartPoint("classificar").flowNodes(nodes)
                .variableDeclarations(List.of(
                        new VariableDeclaration("documento", "Documento", null, VariableFormat.SHORT_TEXT, null, null,
                                null, null, null),
                        new VariableDeclaration("categoria", "Categoria", null, VariableFormat.SINGLE_SELECT,
                                List.of(new VariableOption("FISCAL", "Fiscal"), new VariableOption("CONTRATO", "Contrato")),
                                null, null, null, null)))
                .build();
    }
}
