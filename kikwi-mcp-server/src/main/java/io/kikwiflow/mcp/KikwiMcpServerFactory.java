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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kikwiflow.exception.TaskNotFoundException;
import io.kikwiflow.exception.VariableValidationException;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.mcp.support.TolerantJsonSchemaValidator;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.kikwiflow.model.security.IdentityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Único ponto que conhece o SDK MCP: transforma {@link KikwiTool}/{@link KikwiMcpResources}/{@link KikwiMcpPrompts}
 * num servidor <b>stateless</b> (sem sessão — cada requisição se resolve sozinha, alinhado à spec 2026-07-28 e
 * escalável horizontalmente atrás de qualquer balanceador).
 *
 * <p>Responsabilidades transversais ficam aqui, não nas ferramentas: identidade da chamada, bloqueio de escrita
 * anônima, tradução de exceções do motor em erros legíveis pelo agente e log de auditoria por chamada.
 */
public class KikwiMcpServerFactory {

    public static final String IDENTITY_KEY = "kikwiflow.identity";
    public static final String BASE_URL_KEY = "kikwiflow.baseUrl";
    public static final String SERVER_NAME = "kikwiflow";

    private static final Logger LOGGER = LoggerFactory.getLogger(KikwiMcpServerFactory.class);

    private final ObjectMapper resultMapper;
    private final boolean allowAnonymousWrites;

    /**
     * @param resultMapper mapper do host (com o módulo Jackson do Kikwiflow) para serializar resultados de domínio.
     *                     O protocolo MCP usa um mapper próprio, imune a customizações do host.
     */
    public KikwiMcpServerFactory(ObjectMapper resultMapper, boolean allowAnonymousWrites) {
        this.resultMapper = resultMapper;
        this.allowAnonymousWrites = allowAnonymousWrites;
    }

    public McpStatelessSyncServer build(McpStatelessServerTransport transport, String version, String instructions,
                                        List<KikwiTool> tools, KikwiMcpResources resources) {
        List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs = tools.stream()
                // Ordem determinística: clientes podem cachear tools/list (spec 2026-07-28).
                .sorted(Comparator.comparing(KikwiTool::name))
                .map(this::toSpecification)
                .toList();

        ObjectMapper protocolMapper = new ObjectMapper();
        return McpServer.sync(transport)
                .jsonMapper(new JacksonMcpJsonMapper(protocolMapper))
                .jsonSchemaValidator(new TolerantJsonSchemaValidator(protocolMapper))
                .serverInfo(McpSchema.Implementation.builder(SERVER_NAME, version)
                        .title("Kikwiflow")
                        .description("Consulta, diagnóstico e operação governada do motor de workflow Kikwiflow.")
                        .build())
                .instructions(instructions)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .resourceTemplates(resourceTemplates(resources))
                .prompts(prompts())
                .build();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tools
    // ------------------------------------------------------------------------------------------------------------

    McpStatelessServerFeatures.SyncToolSpecification toSpecification(KikwiTool tool) {
        McpSchema.Tool definition = McpSchema.Tool.builder()
                .name(tool.name())
                .title(tool.title())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .annotations(new McpSchema.ToolAnnotations(tool.title(), tool.readOnly(), tool.destructive(),
                        tool.idempotent(), false, null))
                .build();
        return McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(definition)
                .callHandler((transportContext, request) -> call(tool, callContext(transportContext), request.arguments()))
                .build();
    }

    /** Executa uma ferramenta já com o contexto resolvido — também usado diretamente nos testes. */
    public McpSchema.CallToolResult call(KikwiTool tool, McpCallContext context, Map<String, Object> arguments) {
        long started = System.nanoTime();
        String outcome = "ok";
        try {
            if (tool.write() && context.isAnonymous() && !allowAnonymousWrites) {
                throw new ToolException(ToolException.FORBIDDEN, "A ferramenta " + tool.name() + " altera o motor e exige "
                        + "um usuário autenticado; esta conexão MCP está anônima. Configure a autenticação do cliente MCP "
                        + "(ou, só em demo, kikwiflow.mcp.write-tools.allow-anonymous=true).");
            }
            ToolResult result = tool.handler().handle(new ToolArgs(arguments), context);
            return success(result);
        } catch (RuntimeException e) {
            ToolException error = translate(e, tool);
            outcome = error.getCode();
            return failure(error);
        } finally {
            LOGGER.info("mcp.tool={} actor={} tenant={} outcome={} durationMs={}", tool.name(),
                    context.identity().actorId(), context.identity().tenantId(), outcome,
                    (System.nanoTime() - started) / 1_000_000);
        }
    }

    private McpSchema.CallToolResult success(ToolResult result) {
        Object structured = result.data() == null ? Map.of() : resultMapper.convertValue(result.data(), Object.class);
        if (!(structured instanceof Map)) {
            structured = Map.of("items", structured);
        }
        return McpSchema.CallToolResult.builder()
                .addTextContent(result.summary() + "\n\n" + toJson(structured))
                .structuredContent(structured)
                .isError(false)
                .build();
    }

    private McpSchema.CallToolResult failure(ToolException error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.getCode());
        body.put("message", error.getMessage());
        if (error.getDetails() != null) {
            body.put("details", resultMapper.convertValue(error.getDetails(), Object.class));
        }
        Map<String, Object> structured = Map.of("error", body);
        return McpSchema.CallToolResult.builder()
                .addTextContent("Erro " + error.getCode() + ": " + error.getMessage()
                        + (error.getDetails() != null ? "\n\n" + toJson(body.get("details")) : ""))
                .structuredContent(structured)
                .isError(true)
                .build();
    }

    private ToolException translate(RuntimeException e, KikwiTool tool) {
        if (e instanceof ToolException toolException) {
            return toolException;
        }
        if (e instanceof NotFoundException || e instanceof TaskNotFoundException) {
            return ToolException.notFound(e.getMessage());
        }
        if (e instanceof VariableValidationException validation) {
            return new ToolException(ToolException.CONTRACT_VIOLATION,
                    "Variáveis fora do contrato do nó " + validation.getNodeId() + ".", validation.getErrors());
        }
        if (e instanceof IllegalArgumentException) {
            return ToolException.invalidArgument(e.getMessage());
        }
        if (e instanceof IllegalStateException) {
            return new ToolException(ToolException.CONFLICT, e.getMessage());
        }
        if (e instanceof SecurityException) {
            return new ToolException(ToolException.FORBIDDEN, e.getMessage());
        }
        LOGGER.error("Falha inesperada na ferramenta MCP {}", tool.name(), e);
        return new ToolException("INTERNAL_ERROR", "Erro interno ao executar " + tool.name()
                + ". Detalhes no log do servidor (" + e.getClass().getSimpleName() + ").");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Resources & prompts
    // ------------------------------------------------------------------------------------------------------------

    private List<McpStatelessServerFeatures.SyncResourceTemplateSpecification> resourceTemplates(KikwiMcpResources resources) {
        if (resources == null) {
            return List.of();
        }
        List<McpStatelessServerFeatures.SyncResourceTemplateSpecification> specs = new ArrayList<>();
        specs.add(resourceTemplate(resources, KikwiMcpResources.LATEST_TEMPLATE, "process-definition",
                "Definição implantada (versão mais recente) no formato .kikwi — abre no Kikwiflow Craft."));
        specs.add(resourceTemplate(resources, KikwiMcpResources.VERSION_TEMPLATE, "process-definition-version",
                "Uma versão específica da definição implantada, no formato .kikwi."));
        return specs;
    }

    private McpStatelessServerFeatures.SyncResourceTemplateSpecification resourceTemplate(
            KikwiMcpResources resources, String uriTemplate, String name, String description) {
        McpSchema.ResourceTemplate template = McpSchema.ResourceTemplate.builder(uriTemplate, name)
                .description(description)
                .mimeType(KikwiMcpResources.MIME_TYPE)
                .build();
        return new McpStatelessServerFeatures.SyncResourceTemplateSpecification(template, (transportContext, request) ->
                new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                        request.uri(), KikwiMcpResources.MIME_TYPE, resources.read(request.uri())))));
    }

    private List<McpStatelessServerFeatures.SyncPromptSpecification> prompts() {
        return KikwiMcpPrompts.all().stream().map(prompt -> new McpStatelessServerFeatures.SyncPromptSpecification(
                new McpSchema.Prompt(prompt.name(), prompt.title(), prompt.description(), prompt.arguments().stream()
                        .map(argument -> new McpSchema.PromptArgument(argument.name(), argument.description(), argument.required()))
                        .toList()),
                (transportContext, request) -> new McpSchema.GetPromptResult(prompt.description(), List.of(
                        new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(prompt.render(request.arguments())))))))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------------------

    static McpCallContext callContext(McpTransportContext transportContext) {
        if (transportContext == null) {
            return new McpCallContext(null, null);
        }
        Object identity = transportContext.get(IDENTITY_KEY);
        Object baseUrl = transportContext.get(BASE_URL_KEY);
        return new McpCallContext(identity instanceof IdentityContext id ? id : null,
                baseUrl instanceof String url ? url : null);
    }

    private String toJson(Object value) {
        try {
            return resultMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
