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
package io.kikwiflow.execution;

import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Representa uma instância de processo em execução.
 * <p>
 * Esta classe é um objeto de estado <strong>mutável</strong>, projetado para ser modificado durante o ciclo de vida
 * de uma execução síncrona dentro do {@link FlowNodeExecutor}. Ela carrega o estado "quente" da instância,
 * incluindo variáveis, status atual e metadados.
 * <p>
 * É importante distingui-la do registro imutável {@link io.kikwiflow.model.execution.ProcessInstance},
 * que é usado para persistência, snapshots e comunicação entre os limites do motor.
 */
public class ProcessInstanceExecution {
    private boolean isPersisted = false;
    private String id;
    private String businessKey;
    private ProcessInstanceStatus status;
    private String processDefinitionId;
    private Map<String, ProcessVariable> variables;
    private Instant startedAt;
    private Instant endedAt;
    private BigDecimal businessValue;
    private String tenantId;
    private String origin;
    private int version;
    private Map<String, Integer> activeNodes;
    private final List<BranchPullIntention> branchPullIntentions = new ArrayList<>();

    /**
     * Registra temporariamente em memória que uma branch foi concluída e precisa
     * ser removida do Join Task correspondente durante o commit transacional.
     */
    public void registerBranchConclusion(String joinTaskId, String branchId) {
        this.branchPullIntentions.add(new BranchPullIntention(joinTaskId, branchId));
    }

    /**
     * Retorna as intenções acumuladas para a camada de serviço transacional.
     */
    public List<BranchPullIntention> getBranchPullIntentions() {
        return List.copyOf(this.branchPullIntentions);
    }

    /**
     * Limpa o acumulador após o envio para o UnitOfWork.
     */
    public void clearBranchPullIntentions() {
        this.branchPullIntentions.clear();
    }


    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isPersisted() {
        return isPersisted;
    }

    public Map<String, Integer> getActiveNodes() {
        return activeNodes;
    }

    public void setActiveNodes(Map<String, Integer> activeNodes) {
        this.activeNodes = activeNodes;
    }

    public void setPersisted(boolean persisted) {
        isPersisted = persisted;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public void setBusinessKey(String businessKey) {
        this.businessKey = businessKey;
    }

    public ProcessInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessInstanceStatus status) {
        this.status = status;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public Map<String, ProcessVariable> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, ProcessVariable> variables) {
        this.variables = variables;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public BigDecimal getBusinessValue() {
        return businessValue;
    }

    public void setBusinessValue(BigDecimal businessValue) {
        this.businessValue = businessValue;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void addVariables(Map<String, ProcessVariable> variables) {
        if(variables == null) return;
        this.variables.putAll(variables);
    }
}
