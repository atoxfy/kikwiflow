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

import java.util.ArrayList;
import java.util.List;

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
    private int maxConcurrentTasks = 200;
    private int shutdownGracePeriodSeconds = 20;
    private long lockTimeoutMillis = 5000L;
    private String defaultRetryInterval = "PT3M";
    private int defaultMaxRetries = 3;
    private String instanceName;
    private List<String> fatalExceptions = new ArrayList<>();

    public KikwiflowConfig() {
    }

    public String getDefaultRetryInterval() {
        return defaultRetryInterval;
    }

    public void setDefaultRetryInterval(String defaultRetryInterval) {
        this.defaultRetryInterval = defaultRetryInterval;
    }

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public List<String> getFatalExceptions() {
        return fatalExceptions;
    }

    public void setFatalExceptions(List<String> fatalExceptions) {
        if (fatalExceptions != null) {
            this.fatalExceptions = fatalExceptions;
        }
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setLockTimeoutMillis(long lockTimeoutMillis) {
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    public long getLockTimeoutMillis() {
        return lockTimeoutMillis;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    public void setShutdownGracePeriodSeconds(int shutdownGracePeriodSeconds) {
        this.shutdownGracePeriodSeconds = shutdownGracePeriodSeconds;
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

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    public long getShutdownGracePeriodSeconds() {
        return shutdownGracePeriodSeconds;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("KikwiflowConfig{");
        sb.append("isStatsEnabled=").append(isStatsEnabled);
        sb.append(", isOutboxEventsEnabled=").append(isOutboxEventsEnabled);
        sb.append(", taskAcquisitionIntervalMillis=").append(taskAcquisitionIntervalMillis);
        sb.append(", taskAcquisitionMaxTasks=").append(taskAcquisitionMaxTasks);
        sb.append(", maxConcurrentTasks=").append(maxConcurrentTasks);
        sb.append(", shutdownGracePeriodSeconds=").append(shutdownGracePeriodSeconds);
        sb.append(", lockTimeoutMillis=").append(lockTimeoutMillis);
        sb.append(", instanceName='").append(instanceName).append('\'');
        sb.append(", fatalExceptions=").append(fatalExceptions);
        sb.append('}');
        return sb.toString();
    }
}
