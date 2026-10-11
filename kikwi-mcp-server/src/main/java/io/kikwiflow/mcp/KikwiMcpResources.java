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
import io.kikwiflow.mcp.support.DefinitionLookup;
import io.kikwiflow.mcp.tools.ProcessDefinitionTools;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recurso {@code kikwi://process-definitions/{key}[/versions/{n}]}: a definição implantada no formato de deploy
 * ({@code .kikwi}), o mesmo que o Kikwiflow Craft abre. Permite ao agente trazer o que está em produção para o
 * workspace (comparar com o arquivo local, abrir no Craft) sem acesso ao repositório de origem.
 */
public class KikwiMcpResources {

    public static final String MIME_TYPE = "application/json";
    static final String LATEST_TEMPLATE = ProcessDefinitionTools.RESOURCE_URI_PREFIX + "{key}";
    static final String VERSION_TEMPLATE = ProcessDefinitionTools.RESOURCE_URI_PREFIX + "{key}/versions/{version}";

    /** Campos do formato .kikwi/deploy, na ordem em que o Craft grava. id/version/checksum são do servidor. */
    private static final String[] KIKWI_FIELDS = {"key", "name", "description", "sla", "defaultStartPoint",
            "variableDeclarations", "extensionProperties", "flowNodes"};

    private final DefinitionLookup definitions;
    private final ObjectMapper objectMapper;

    public KikwiMcpResources(QueryRepository queryRepository, ObjectMapper objectMapper) {
        this.definitions = new DefinitionLookup(queryRepository);
        this.objectMapper = objectMapper;
    }

    /** @return o JSON .kikwi da definição apontada pela URI. */
    public String read(String uri) {
        if (uri == null || !uri.startsWith(ProcessDefinitionTools.RESOURCE_URI_PREFIX)) {
            throw ToolException.notFound("Recurso desconhecido: " + uri);
        }
        String path = uri.substring(ProcessDefinitionTools.RESOURCE_URI_PREFIX.length());
        String key = path;
        Integer version = null;
        int versionsAt = path.indexOf("/versions/");
        if (versionsAt >= 0) {
            key = path.substring(0, versionsAt);
            try {
                version = Integer.parseInt(path.substring(versionsAt + "/versions/".length()));
            } catch (NumberFormatException e) {
                throw ToolException.invalidArgument("Versão inválida em " + uri);
            }
        }
        if (key.isBlank() || key.contains("/")) {
            throw ToolException.notFound("Recurso desconhecido: " + uri);
        }
        return toKikwiJson(definitions.byKey(key, version));
    }

    @SuppressWarnings("unchecked")
    String toKikwiJson(ProcessDefinition definition) {
        Map<String, Object> full = objectMapper.convertValue(definition, Map.class);
        Map<String, Object> kikwi = new LinkedHashMap<>();
        for (String field : KIKWI_FIELDS) {
            if (full.get(field) != null) {
                kikwi.put(field, full.get(field));
            }
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(kikwi);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a definição " + definition.key(), e);
        }
    }
}
