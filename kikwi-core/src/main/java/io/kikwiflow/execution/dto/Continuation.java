/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.execution.dto;

import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;

import java.util.List;

/**
 * Carrega a decisão de roteamento do Navigator e seus respectivos metadados de escopo.
 * <p>
 * {@code nextNodeKeys}/{@code targetJoinNodeKey} carregam a chave usada em
 * {@code processDefinition.flowNodes().get(...)} para resolver cada entrada de {@code nextNodes()}/
 * {@code targetJoinNode()} — a engine usa exclusivamente essas chaves (nunca o campo {@code id()} interno do
 * nó) para gravar {@code taskDefinitionId} em runtime, já que a chave do mapa é a única forma de identificador
 * garantidamente consistente com o resto do motor (ver docs/engine/15-achados-motor-lacunas-de-validacao.md,
 * §2.2). Sempre do mesmo tamanho/ordem que {@code nextNodes()}.
 */
public record Continuation(
        List<FlowNodeDefinition> nextNodes,
        List<String> nextNodeKeys,
        boolean isAsynchronous,
        String resolvedAnswer,
        String chosenFlowId,
        FlowNodeDefinition targetJoinNode,
        String targetJoinNodeKey
) {
    public Continuation(List<FlowNodeDefinition> nextNodes, List<String> nextNodeKeys, boolean isAsynchronous) {
        this(nextNodes, nextNodeKeys, isAsynchronous, null, null, null, null);
    }

    public Continuation(List<FlowNodeDefinition> nextNodes, List<String> nextNodeKeys, boolean isAsynchronous, String resolvedAnswer, String chosenFlowId) {
        this(nextNodes, nextNodeKeys, isAsynchronous, resolvedAnswer, chosenFlowId, null, null);
    }
}
