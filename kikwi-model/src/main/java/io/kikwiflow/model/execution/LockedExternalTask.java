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

package io.kikwiflow.model.execution;

import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.definition.process.variable.VariableDeclaration;
import io.kikwiflow.model.execution.node.ExternalTask;

import java.util.List;
import java.util.Map;

/**
 * O que um worker recebe no {@code fetchAndLock} (docs/engine/31, E2): a tarefa já travada e tudo o que ele
 * precisa para trabalhá-la sem outra chamada ao motor.
 *
 * @param variables    as variáveis de {@code inputs} do nó, ou todas quando o nó não declara inputs
 * @param node         a definição do nó (num {@code AGENT_TASK}: {@code agentRef}, {@code tools}, {@code limits} e
 *                     o objetivo em {@code extensionProperties["kikwi:prompt"]})
 * @param declarations as declarações do catálogo das variáveis de inputs e outputs do nó (rótulo, formato,
 *                     opções), para o worker montar o contrato de saída
 */
public record LockedExternalTask(
        ExternalTask task,
        String businessKey,
        Map<String, ProcessVariable> variables,
        FlowNodeDefinition node,
        List<VariableDeclaration> declarations
) {
}
