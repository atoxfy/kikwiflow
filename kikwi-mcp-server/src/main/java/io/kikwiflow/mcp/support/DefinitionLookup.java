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

package io.kikwiflow.mcp.support;

import io.kikwiflow.mcp.ToolException;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.persistence.api.repository.QueryRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolução de definições por chave/versão/id com mensagens de erro que orientam o agente. */
public class DefinitionLookup {

    private final QueryRepository queryRepository;

    public DefinitionLookup(QueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public ProcessDefinition byKey(String key, Integer version) {
        if (version == null) {
            return queryRepository.findLatestVersionByKey(key)
                    .orElseThrow(() -> unknownKey(key));
        }
        return versionsOf(key).stream()
                .filter(definition -> Objects.equals(definition.version(), version))
                .findFirst()
                .orElseThrow(() -> ToolException.notFound("O processo `" + key + "` não tem a versão " + version
                        + ". Use describe_process sem `version` para a versão atual."));
    }

    public Optional<ProcessDefinition> byId(String processDefinitionId) {
        return processDefinitionId == null ? Optional.empty() : queryRepository.findProcessDefinitionById(processDefinitionId);
    }

    /** Todas as versões implantadas de uma chave, da mais nova para a mais antiga. */
    public List<ProcessDefinition> versionsOf(String key) {
        List<ProcessDefinition> found = queryRepository.findAProcessDefinitionsByParams(key);
        List<ProcessDefinition> versions = found == null ? List.of() : found.stream()
                .filter(definition -> key.equals(definition.key()))
                .sorted(Comparator.comparing(ProcessDefinition::version, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        if (versions.isEmpty()) {
            throw unknownKey(key);
        }
        return versions;
    }

    public List<String> versionIdsOf(String key) {
        return versionsOf(key).stream().map(ProcessDefinition::id).toList();
    }

    private static ToolException unknownKey(String key) {
        return ToolException.notFound("Nenhum processo implantado com a chave `" + key
                + "`. Use list_process_definitions para ver as chaves existentes.");
    }
}
