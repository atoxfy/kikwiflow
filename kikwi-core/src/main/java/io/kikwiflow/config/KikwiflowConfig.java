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
package io.kikwiflow.config;

public class KikwiflowConfig {

    /**
     * Controla se a engine deve coletar estatísticas de execução para cada nó do fluxo.
     * <p>
     * Quando habilitado ({@code true}), o motor gera eventos leves (lightweight events) como {@code FlowNodeExecutionStats}
     * que contêm métricas de desempenho (tempos de início e fim). Isso é útil para
     * monitoramento e análise, mas por se tratar de uma publicação assincrona (fire and forget) inconsistências
     * podem ocorrer.
     * <p>
     * O valor padrão é {@code false}. Pode ser sobrescrito via {@code kikwiflow.stats.enabled=true}
     * no arquivo de propriedades da aplicação.
     */
    private boolean isStatsEnabled = false;
    /**
     * Controla o uso do Padrão Outbox para a publicação de eventos críticos.
     * <p>
     * Quando habilitado ({@code true}), os eventos gerados durante a execução do processo
     * (como {@code FlowNodeExecuted} ou {@code ProcessInstanceFinished}) são primeiro salvos
     * em uma "caixa de saída" persistente, dentro da mesma transação da mudança de estado do processo.
     * Um processo separado (relay) é então responsável por ler desta caixa e dar um destino aos eventos,
     *garantindo a entrega e a consistência transacional.
     * <p>
     * O valor padrão é {@code false}. Pode ser sobrescrito via {@code kikwiflow.outbox.events-enabled=true}
     * no arquivo de propriedades da aplicação.
     */
    private boolean isOutboxEventsEnabled = false;


    private long taskAcquisitionIntervalMillis = 5000L;
    private int taskAcquisitionMaxTasks = 10;


    public KikwiflowConfig() {
    }

    public long getTaskAcquisitionIntervalMillis() {
        return taskAcquisitionIntervalMillis;
    }

    public void setTaskAcquisitionIntervalMillis(long taskAcquisitionIntervalMillis) {
        this.taskAcquisitionIntervalMillis = taskAcquisitionIntervalMillis;
    }

    public int getTaskAcquisitionMaxTasks() {
        return taskAcquisitionMaxTasks;
    }

    public void setTaskAcquisitionMaxTasks(int taskAcquisitionMaxTasks) {
        this.taskAcquisitionMaxTasks = taskAcquisitionMaxTasks;
    }

    public void statsEnabled() {
        this.isStatsEnabled = true;
    }
    public void outboxEventsEnabled() {
        this.isOutboxEventsEnabled = true;
    }

    public void statsDisabled(){
        this.isStatsEnabled = false;
    }

    public boolean isStatsEnabled(){
        return isStatsEnabled;
    }

    public boolean isOutboxEventsEnabled() {
        return isOutboxEventsEnabled;
    }
}
