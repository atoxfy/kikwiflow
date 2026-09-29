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
package io.kikwiflow.management.history;

import io.kikwiflow.model.event.HistoryEventSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.persistence.api.history.HistoricInstanceSummary;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O "trace" de uma instância para o explorador de histórico (docs/engine/29): a linha do tempo completa e,
 * derivado dela, o que o canvas precisa para redesenhar o caminho percorrido.
 *
 * @param instance         cabeçalho vindo de STARTED/FINISHED; {@code null} se a instância começou com o outbox
 *                         desligado (só há eventos a partir de quando ele foi ligado).
 * @param activeNodes      onde a instância está agora (nó → tokens), vazio se ela já terminou.
 * @param events           todos os eventos, na ordem do histórico, com variáveis já mascaradas para quem lê.
 * @param visitedNodes     nó → nº de passagens concluídas (inclui tarefas humanas, esperas e timers que dispararam).
 * @param interruptedNodes nós cuja última passagem foi interrompida (ex.: SLA): visitados, mas não saíram pelas
 *                         próprias setas.
 * @param takenFlowIds     setas escolhidas por gateways exclusivos (id do fluxo no .kikwi = id da aresta no canvas).
 * @param decisions        respostas de gateway, em ordem.
 * @param humanActions     conclusões de tarefa externa, com quem concluiu e o que enviou, em ordem.
 * @param finalVariables   variáveis no fim da instância (ou atuais, se ainda ativa), mascaradas.
 */
public record InstanceTrace(
        HistoricInstanceSummary instance,
        Map<String, Integer> activeNodes,
        List<HistoryEventSummary> events,
        Map<String, Integer> visitedNodes,
        Set<String> interruptedNodes,
        List<String> takenFlowIds,
        List<TraceDecision> decisions,
        List<TraceHumanAction> humanActions,
        Map<String, ProcessVariable> finalVariables
) {
}
