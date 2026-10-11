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
import io.kikwiflow.management.dtos.ProcessInstanceSnapshot;
import io.kikwiflow.management.service.ProcessInstanceSnapshotService;
import io.kikwiflow.mcp.support.ConfirmationTokenService;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.ExecutableTaskDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.elements.StartEventDefinition;
import io.kikwiflow.model.definition.process.variable.VariableBinding;
import io.kikwiflow.model.definition.process.variable.VariableBindings;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.definition.process.variable.VariableFormat;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.IncidentStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.security.IdentityContext;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.query.IncidentQuery;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.query.VariableAggregation;
import io.kikwiflow.persistence.api.query.VariableAggregationResult;
import io.kikwiflow.model.definition.process.variable.VariableNumberFormat;
import io.kikwiflow.persistence.api.query.VariableProfile;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.mcp.support.MonitorLinks;
import io.kikwiflow.mcp.tools.AggregationTools;
import io.kikwiflow.security.DefaultVariableSecurityPolicyManager;
import io.kikwiflow.security.api.VariableSecurityPolicyManager;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Ferramentas MCP do Kikwiflow")
class KikwiMcpToolsTest {

    private static final IdentityContext ANA = new IdentityContext("ana", null);
    private static final String BASE_URL = "http://localhost:8081";

    private QueryRepository queryRepository;
    private KikwiflowEngine engine;
    private ProcessInstanceSnapshotService snapshotService;
    private IncidentQuery incidentQuery;
    private ProcessInstanceQuery instanceQuery;
    private KikwiMcpServerFactory factory;
    private Map<String, KikwiTool> tools;

    @BeforeEach
    void setUp() {
        queryRepository = mock(QueryRepository.class);
        engine = mock(KikwiflowEngine.class);
        snapshotService = mock(ProcessInstanceSnapshotService.class);
        incidentQuery = mock(IncidentQuery.class, RETURNS_SELF);
        instanceQuery = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(queryRepository.createIncidentQuery()).thenReturn(incidentQuery);
        when(queryRepository.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.listSummary()).thenReturn(new PageResult<>(List.of(), 0, 0, 0, 1));

        ProcessDefinition definition = definition();
        when(queryRepository.findProcessDefinitionById("def-1")).thenReturn(Optional.of(definition));
        when(queryRepository.findLatestVersionByKey("kyc")).thenReturn(Optional.of(definition));
        when(queryRepository.findAProcessDefinitionsByParams("kyc")).thenReturn(List.of(definition));

        ConfirmationTokenService tokens = new ConfirmationTokenService(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5), Clock.systemUTC());
        factory = new KikwiMcpServerFactory(new ObjectMapper().findAndRegisterModules(), false);
        tools = new LinkedHashMap<>();
        KikwiMcpCatalog.tools(engine, queryRepository, snapshotService, new DefaultVariableSecurityPolicyManager(), tokens,
                        new KikwiMcpCatalog.Options(true, 20, 2000, "/monitor-ui"), Clock.systemUTC())
                .forEach(tool -> tools.put(tool.name(), tool));
    }

    @Test
    @DisplayName("O catálogo traz as ferramentas de leitura e, com escrita ligada, start/retry marcadas como escrita")
    void catalog() {
        assertTrue(tools.keySet().containsAll(List.of("list_process_definitions", "describe_process", "get_start_form",
                "get_process_health", "search_process_instances", "get_process_instance", "get_instance_timeline",
                "search_incidents", "diagnose_incident", "search_external_tasks", "get_external_task_form",
                "aggregate_process_variables", "profile_process_variables",
                "start_process_instance", "retry_incidents")));
        assertTrue(tools.get("retry_incidents").destructive());
        assertTrue(tools.get("retry_incidents").write());
        assertTrue(tools.get("diagnose_incident").readOnly());
        assertFalse(tools.get("retry_incidents").description().contains("%d"));
    }

    @Nested
    @DisplayName("retry_incidents")
    class RetryIncidents {

        @BeforeEach
        void incidents() {
            when(queryRepository.findIncidentById("i-1")).thenReturn(Optional.of(incident("i-1", IncidentStatus.OPEN)));
            when(queryRepository.findIncidentById("i-2")).thenReturn(Optional.of(incident("i-2", IncidentStatus.RESOLVED)));
        }

        @Test
        @DisplayName("Dado uma conexão anônima, Quando pede retry, Então é recusado sem tocar o motor")
        void anonymousIsForbidden() {
            McpSchema.CallToolResult result = call("retry_incidents", new IdentityContext("anonymous", null),
                    Map.of("incidentIds", List.of("i-1")));

            assertEquals("FORBIDDEN", errorCode(result));
            verify(engine, never()).retryIncident(anyString(), any());
        }

        @Test
        @DisplayName("Dado ids, Quando pede retry sem token, Então só faz preview e emite token para os elegíveis")
        void previewDoesNotExecute() {
            McpSchema.CallToolResult result = call("retry_incidents", ANA, Map.of("incidentIds", List.of("i-1", "i-2")));

            Map<String, Object> body = structured(result);
            assertEquals(false, body.get("executed"));
            assertEquals(1, ((Number) body.get("eligibleCount")).intValue());
            assertNotNull(body.get("confirmationToken"));
            assertEquals(1, ((List<?>) body.get("skipped")).size());
            verify(engine, never()).retryIncident(anyString(), any());
        }

        @Test
        @DisplayName("Quando confirma com o token, Então reprocessa exatamente o preview, em nome do usuário")
        void confirmExecutes() {
            String token = (String) structured(call("retry_incidents", ANA, Map.of("incidentIds", List.of("i-1", "i-2"))))
                    .get("confirmationToken");

            McpSchema.CallToolResult result = call("retry_incidents", ANA, Map.of("confirmationToken", token));

            assertEquals(1, ((Number) structured(result).get("retriedCount")).intValue());
            verify(engine, times(1)).retryIncident(eq("i-1"), argThat(identity -> "ana".equals(identity.actorId())));
            verify(engine, never()).retryIncident(eq("i-2"), any());
        }

        @Test
        @DisplayName("Quando outro usuário reaproveita o token, Então é recusado")
        void tokenIsBoundToActor() {
            String token = (String) structured(call("retry_incidents", ANA, Map.of("incidentIds", List.of("i-1"))))
                    .get("confirmationToken");

            McpSchema.CallToolResult result = call("retry_incidents", new IdentityContext("bruno", null),
                    Map.of("confirmationToken", token));

            assertEquals("INVALID_CONFIRMATION", errorCode(result));
            verify(engine, never()).retryIncident(anyString(), any());
        }
    }

    @Nested
    @DisplayName("aggregate_process_variables")
    class AggregateProcessVariables {

        @Test
        @DisplayName("Quando pergunta quanto está parado numa etapa, Então filtra ACTIVE no nó e devolve soma, ausentes e não numéricos")
        void moneyParkedInAStage() {
            VariableAggregationResult.MetricValue compra = new VariableAggregationResult.MetricValue(
                    new BigDecimal("70000.00"), new BigDecimal("35000.00"), new BigDecimal("30000"), new BigDecimal("40000.00"), 2, 3);
            VariableAggregationResult.MetricValue spread = new VariableAggregationResult.MetricValue(
                    BigDecimal.ZERO, null, null, null, 0, 0);
            when(instanceQuery.aggregate(any())).thenReturn(new VariableAggregationResult(
                    new VariableAggregationResult.Group(null, 4, Map.of("compra.valor", compra, "spread", spread)), List.of(), false));

            Map<String, Object> body = structured(call("aggregate_process_variables", ANA, Map.of(
                    "processDefinitionKey", "kyc",
                    "activeNodeId", "analisar",
                    "metrics", List.of(Map.of("variable", "compra.valor"),
                            Map.of("variable", "venda.valor", "minus", "compra.valor", "label", "spread")))));

            verify(instanceQuery).processDefinitionKeyIn(List.of("kyc"));
            verify(instanceQuery).activeNodeId("analisar");
            verify(instanceQuery).statusIn(List.of(ProcessInstanceStatus.ACTIVE));
            verify(instanceQuery).aggregate(argThat(a -> a.groupBy() == VariableAggregation.GroupBy.NONE
                    && a.metrics().get(1).isDifference()));
            @SuppressWarnings("unchecked")
            Map<String, Object> filters = (Map<String, Object>) body.get("filters");
            assertEquals("Analisar documento", filters.get("activeNodeName"));
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> totals = (Map<String, Map<String, Object>>) body.get("totals");
            assertEquals(new BigDecimal("70000.00"), totals.get("compra.valor").get("sum"));
            assertEquals(1L, totals.get("compra.valor").get("missing"));
            assertEquals(1L, totals.get("compra.valor").get("nonNumeric"));
            assertTrue(body.get("notes").toString().contains("spread"), "variável ausente em todas vira nota orientando a conferir a chave");
        }

        @Test
        @DisplayName("Quando o catálogo declara numberFormat, Então a soma lê o texto nesse formato e diz de onde veio")
        void numberFormatFromCatalog() {
            VariableAggregationResult.MetricValue valor = new VariableAggregationResult.MetricValue(
                    new BigDecimal("45000.00"), new BigDecimal("45000.00"), null, null, 1, 1);
            when(instanceQuery.aggregate(any())).thenReturn(new VariableAggregationResult(
                    new VariableAggregationResult.Group(null, 1, Map.of("valor", valor)), List.of(), false));

            Map<String, Object> body = structured(call("aggregate_process_variables", ANA, Map.of(
                    "processDefinitionKey", "kyc", "metrics", List.of(Map.of("variable", "valor")))));

            verify(instanceQuery).aggregate(argThat(a -> a.formatOf("valor") == VariableNumberFormat.PT_BR));
            assertEquals(Map.of("valor", Map.of("format", "PT_BR", "source", "catalog")), body.get("numberFormats"));
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> totals = (Map<String, Map<String, Object>>) body.get("totals");
            assertEquals("BRL", totals.get("valor").get("currency"));
        }

        @Test
        @DisplayName("Quando a variável não está no catálogo, Então soma em PLAIN e avisa que o significado foi inferido")
        void undeclaredVariableIsFlagged() {
            VariableAggregationResult.MetricValue outro = new VariableAggregationResult.MetricValue(
                    BigDecimal.TEN, BigDecimal.TEN, null, null, 1, 1);
            when(instanceQuery.aggregate(any())).thenReturn(new VariableAggregationResult(
                    new VariableAggregationResult.Group(null, 1, Map.of("outro", outro)), List.of(), false));

            Map<String, Object> body = structured(call("aggregate_process_variables", ANA, Map.of(
                    "processDefinitionKey", "kyc", "metrics", List.of(Map.of("variable", "outro")))));

            assertEquals(Map.of("outro", Map.of("format", "PLAIN", "source", "default")), body.get("numberFormats"));
            assertTrue(body.get("notes").toString().contains("não está no catálogo"));
        }

        @Test
        @DisplayName("Quando falta a métrica ou o groupByVariable, Então INVALID_ARGUMENT")
        void invalidArguments() {
            assertEquals("INVALID_ARGUMENT", errorCode(call("aggregate_process_variables", ANA,
                    Map.of("processDefinitionKey", "kyc", "metrics", List.of()))));
            assertEquals("INVALID_ARGUMENT", errorCode(call("aggregate_process_variables", ANA,
                    Map.of("processDefinitionKey", "kyc", "groupBy", "VARIABLE",
                            "metrics", List.of(Map.of("variable", "compra.valor"))))));
            verify(instanceQuery, never()).aggregate(any());
        }

        @Test
        @DisplayName("Quando a política esconde a variável desta identidade, Então FORBIDDEN e nada é somado")
        void maskedVariablesCannotBeSummed() {
            VariableSecurityPolicyManager policy = mock(VariableSecurityPolicyManager.class);
            when(policy.canRead(eq("def-1"), any(), any(java.util.Set.class))).thenReturn(false);
            KikwiTool tool = new AggregationTools(queryRepository, policy, MonitorLinks.disabled()).tools().stream()
                    .filter(t -> t.name().equals("aggregate_process_variables")).findFirst().orElseThrow();

            McpSchema.CallToolResult result = factory.call(tool, new McpCallContext(ANA, BASE_URL), Map.of(
                    "processDefinitionKey", "kyc", "metrics", List.of(Map.of("variable", "salario"))));

            assertEquals("FORBIDDEN", errorCode(result));
            verify(instanceQuery, never()).aggregate(any());
        }
    }

    @Nested
    @DisplayName("profile_process_variables")
    class ProfileProcessVariables {

        @Test
        @DisplayName("Quando perfila, Então cruza o que é gravado com o catálogo e aponta o que falta declarar")
        void crossesDataWithCatalog() {
            when(instanceQuery.profileVariables(anyInt())).thenReturn(new VariableProfile(10, List.of(
                    new VariableProfile.KeyProfile("valor", 10, Map.of("string", 10L), 0, 10),
                    new VariableProfile.KeyProfile("observacao", 3, Map.of("string", 3L), 0, 0))));

            Map<String, Object> body = structured(call("profile_process_variables", ANA, Map.of("processDefinitionKey", "kyc")));

            verify(instanceQuery).processDefinitionKeyIn(List.of("kyc"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> variables = (List<Map<String, Object>>) body.get("variables");
            Map<String, Object> valor = variables.get(0);
            assertEquals(true, valor.get("declared"));
            assertEquals("Valor do negócio", valor.get("label"));
            assertEquals("PT_BR", valor.get("catalogNumberFormat"));
            assertEquals(new BigDecimal("100.0"), valor.get("presentPct"));
            @SuppressWarnings("unchecked")
            Map<String, Object> numeric = (Map<String, Object>) valor.get("numeric");
            assertEquals("PT_BR", numeric.get("suggestedNumberFormat"));
            assertEquals(List.of("observacao"), body.get("undeclared"));
            assertEquals(List.of("documento"), body.get("declaredNotSeen"));
        }

        @Test
        @DisplayName("Quando a política esconde uma variável, Então ela some do perfil e só a contagem aparece")
        void hidesUnreadableKeys() {
            when(instanceQuery.profileVariables(anyInt())).thenReturn(new VariableProfile(10, List.of(
                    new VariableProfile.KeyProfile("salario", 10, Map.of("double", 10L), 10, 10))));
            VariableSecurityPolicyManager policy = mock(VariableSecurityPolicyManager.class);
            when(policy.canRead(eq("def-1"), any(), eq("salario"))).thenReturn(false);
            KikwiTool tool = new AggregationTools(queryRepository, policy, MonitorLinks.disabled()).tools().stream()
                    .filter(t -> t.name().equals("profile_process_variables")).findFirst().orElseThrow();

            Map<String, Object> body = structured(factory.call(tool, new McpCallContext(ANA, BASE_URL),
                    Map.of("processDefinitionKey", "kyc")));

            assertEquals(List.of(), body.get("variables"));
            assertEquals(1, body.get("hiddenByPolicy"));
        }
    }

    @Nested
    @DisplayName("start_process_instance")
    class StartProcessInstance {

        @Test
        @DisplayName("Quando falta variável obrigatória do início, Então devolve as violações e não inicia")
        void contractViolation() {
            McpSchema.CallToolResult result = call("start_process_instance", ANA,
                    Map.of("key", "kyc", "businessKey", "P-1", "variables", Map.of()));

            assertEquals("CONTRACT_VIOLATION", errorCode(result));
            verify(engine, never()).startProcess();
        }

        @Test
        @DisplayName("Com dryRun e contrato ok, Então valida sem iniciar")
        void dryRun() {
            McpSchema.CallToolResult result = call("start_process_instance", ANA,
                    Map.of("key", "kyc", "businessKey", "P-1", "variables", Map.of("documento", "doc.pdf"), "dryRun", true));

            assertFalse(result.isError());
            assertEquals(true, structured(result).get("valid"));
            verify(engine, never()).startProcess();
        }

        @Test
        @DisplayName("Quando já existe instância ativa com o mesmo businessKey, Então recusa com CONFLICT")
        void duplicateBusinessKey() {
            when(instanceQuery.listSummary()).thenReturn(new PageResult<>(List.of(new io.kikwiflow.model.execution.ProcessInstanceSummary(
                    "pi-9", "P-1", ProcessInstanceStatus.ACTIVE, "def-1", Instant.now(), null, Map.of(), null, null, null)), 1, 1, 0, 1));

            McpSchema.CallToolResult result = call("start_process_instance", ANA,
                    Map.of("key", "kyc", "businessKey", "P-1", "variables", Map.of("documento", "doc.pdf")));

            assertEquals("CONFLICT", errorCode(result));
            verify(engine, never()).startProcess();
        }
    }

    @Nested
    @DisplayName("diagnose_incident")
    class DiagnoseIncident {

        @Test
        @DisplayName("Dado um timeout de dependência, Então classifica como TRANSIENT e traz nó, contrato e link do Monitor")
        void transientFailure() {
            Incident incident = new Incident("i-1", "FAILED_TASK", "Read timed out calling KYC provider",
                    "java.net.SocketTimeoutException: Read timed out\n\tat com.acme.Kyc.call(Kyc.java:42)\nCaused by: java.net.SocketTimeoutException: Read timed out",
                    "def-1", "pi-1", "et-1", Instant.now(), IncidentStatus.OPEN, "analisar");
            when(queryRepository.findIncidentById("i-1")).thenReturn(Optional.of(incident));
            when(queryRepository.findProcessInstanceById("pi-1")).thenReturn(Optional.of(instance("t1")));
            when(incidentQuery.count()).thenReturn(3L);

            Map<String, Object> body = structured(call("diagnose_incident", ANA, Map.of("incidentId", "i-1")));

            @SuppressWarnings("unchecked")
            Map<String, Object> assessment = (Map<String, Object>) body.get("assessment");
            assertEquals("TRANSIENT", assessment.get("category"));
            assertNotNull(assessment.get("systemic"));
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) body.get("failedNode");
            assertEquals("llmTaskHandler", node.get("executor"));
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) body.get("incident");
            assertEquals(BASE_URL + "/monitor-ui/monitor?processId=def-1&instance=pi-1", row.get("monitorUrl"));
        }
    }

    @Test
    @DisplayName("Dado uma identidade de outro tenant, Então a instância responde NOT_FOUND (não revela que existe)")
    void tenantIsolation() {
        when(snapshotService.getSnapshot("pi-1")).thenReturn(
                new ProcessInstanceSnapshot(instance("t1"), List.of(), List.of(), List.of(), List.of()));

        McpSchema.CallToolResult result = call("get_process_instance", new IdentityContext("carla", "t2"),
                Map.of("processInstanceId", "pi-1"));

        assertEquals("NOT_FOUND", errorCode(result));
    }

    @Test
    @DisplayName("Valores grandes de variáveis são truncados na resposta")
    void truncatesLargeVariables() {
        ProcessInstance instance = ProcessInstance.builder().id("pi-1").processDefinitionId("def-1").businessKey("P-1")
                .status(ProcessInstanceStatus.ACTIVE).startedAt(Instant.now()).tenantId("t1")
                .variables(Map.of("iaRespostaBruta", new ProcessVariable("iaRespostaBruta", false, "x".repeat(5000))))
                .build();
        when(snapshotService.getSnapshot("pi-1")).thenReturn(
                new ProcessInstanceSnapshot(instance, List.of(), List.of(), List.of(), List.of()));

        Map<String, Object> body = structured(call("get_process_instance", ANA, Map.of("processInstanceId", "pi-1")));

        @SuppressWarnings("unchecked")
        String value = (String) ((Map<String, Object>) body.get("variables")).get("iaRespostaBruta");
        assertTrue(value.length() < 2100);
        assertTrue(value.contains("truncado"));
    }

    // ------------------------------------------------------------------------------------------------------------

    private McpSchema.CallToolResult call(String tool, IdentityContext identity, Map<String, Object> arguments) {
        return factory.call(tools.get(tool), new McpCallContext(identity, BASE_URL), arguments);
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

    private static Incident incident(String id, IncidentStatus status) {
        return new Incident(id, "FAILED_TASK", "boom", null, "def-1", "pi-1", "et-1", Instant.now(), status, "analisar");
    }

    private static ProcessInstance instance(String tenant) {
        return ProcessInstance.builder().id("pi-1").processDefinitionId("def-1").businessKey("P-1")
                .status(ProcessInstanceStatus.ACTIVE).startedAt(Instant.now()).tenantId(tenant)
                .variables(Map.of("documento", new ProcessVariable("documento", false, "doc.pdf")))
                .activeNodes(Map.of("analisar", 1))
                .build();
    }

    private static ProcessDefinition definition() {
        VariableBindings startInputs = new VariableBindings(List.of(new VariableBinding("documento", true)), List.of());
        Map<String, FlowNodeDefinition> nodes = new LinkedHashMap<>();
        nodes.put("inicio", StartEventDefinition.builder().id("inicio").name("Início").variableBindings(startInputs).build());
        nodes.put("analisar", ExecutableTaskDefinition.builder().id("analisar").name("Analisar documento")
                .executor("llmTaskHandler")
                .extensionProperties(Map.of("kikwi:prompt", "Analise {{documento}}"))
                .variableBindings(new VariableBindings(List.of(new VariableBinding("documento", true)), List.of()))
                .build());
        return ProcessDefinition.builder().id("def-1").key("kyc").name("Análise KYC").version(3)
                .defaultStartPoint("inicio").flowNodes(nodes)
                .variableDeclarations(List.of(new VariableDeclaration("documento", "Documento", null,
                        VariableFormat.SHORT_TEXT, null, null, null, null, null),
                        new VariableDeclaration("valor", "Valor do negócio", "Valor pago ao cliente", VariableFormat.MONEY,
                                null, "BRL", null, null, Map.of(VariableNumberFormat.CATALOG_PROPERTY, "PT_BR"))))
                .build();
    }
}
