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

package io.kikwiflow.management.dtos;

import io.kikwiflow.model.execution.enumerated.MatchPolicy;

import java.util.List;

/**
 * Resumo pronto-para-monitor do progresso de um {@code EVENT_CATCHER} parado numa instância — "3 de 5 eventos
 * recebidos". Computado por {@code EventCatcherWaitStatusMapper} a partir das {@code ExternalTask} cruas que já
 * trafegam em {@link ProcessInstanceSnapshot#externalTasks()}; não é um campo persistido, é derivado a cada
 * snapshot.
 * <p>
 * Um {@code STANDALONE} (single-key) aparece aqui com {@code totalCorrelationKeys=1},
 * {@code receivedCount=0} — só existe uma entrada de espera enquanto a chave não chega; quando chega, a tarefa
 * é apagada e some do snapshot (não vira uma entrada com {@code receivedCount=1}).
 *
 * @param taskDefinitionId       id do nó {@code EVENT_CATCHER} na definição do processo — cruza com o diagrama.
 * @param externalTaskId         id da {@code ExternalTask} coordenadora (GROUP) ou da própria tarefa (STANDALONE).
 * @param matchPolicy            {@code null} para STANDALONE (não se aplica); {@code ALL}/{@code ANY} para GROUP.
 * @param totalCorrelationKeys   quantas chaves de correlação esse nó está esperando ao todo.
 * @param receivedCount          quantas já chegaram — contado pelas tarefas-filha com status {@code CORRELATED},
 *                                não pelo campo {@code pendingCorrelationKeys} da mãe (que não é confiável para
 *                                {@code matchPolicy=ANY}, onde a lista não é reduzida por já haver um vencedor).
 * @param pendingCorrelationKeys chaves ainda não recebidas — o que falta para o nó concluir.
 */
public record KKFEventCatcherWaitStatus(
        String taskDefinitionId,
        String externalTaskId,
        MatchPolicy matchPolicy,
        int totalCorrelationKeys,
        int receivedCount,
        List<String> pendingCorrelationKeys
) {
}
