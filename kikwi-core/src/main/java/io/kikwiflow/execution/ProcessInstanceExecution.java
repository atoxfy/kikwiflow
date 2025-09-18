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

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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
    private String id;
    private String businessKey;
    private ProcessInstanceStatus status;
    private String processDefinitionId;
    private Map<String, ProcessVariable> variables;
    private Instant startedAt;
    private Instant endedAt;
    private BigDecimal businessValue;
    private String tenantId;
    
    
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

    public static Builder builder() {
        return new Builder();
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

    /**
     * Um builder para criar instâncias de {@link ProcessInstanceExecution} de forma fluente.
     * <p>
     * Simplifica a criação do objeto, especialmente ao definir os parâmetros iniciais de um processo.
     */
    public static class Builder {
        private String businessKey;
        private String processDefinitionId;
        private Map<String, ProcessVariable> variables;
        private BigDecimal businessValue;
        private String tenantId;

        private Builder() {
        }

        /**
         * Define a chave de negócio para a instância do processo.
         * @param businessKey A chave de negócio.
         * @return O próprio builder, para encadeamento de chamadas.
         */
        public Builder businessKey(String businessKey) {
            this.businessKey = businessKey;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }


        public Builder businessValue(BigDecimal businessValue) {
            this.businessValue = businessValue;
            return this;
        }

        /**
         * Define o ID da definição de processo à qual esta instância pertence.
         * @param processDefinitionId O ID da definição.
         * @return O próprio builder, para encadeamento de chamadas.
         */
        public Builder processDefinitionId(String processDefinitionId) {
            this.processDefinitionId = processDefinitionId;
            return this;
        }

        /**
         * Define o mapa de variáveis iniciais para a instância do processo.
         * @param variables O mapa de variáveis.
         * @return O próprio builder, para encadeamento de chamadas.
         */
        public Builder variables(Map<String, ProcessVariable> variables) {
            this.variables = variables;
            return this;
        }

        /**
         * Constrói e retorna a nova instância de {@link ProcessInstanceExecution} com o estado inicial configurado.
         * <p>
         * O status é inicializado como {@link ProcessInstanceStatus#ACTIVE} e o tempo de início é definido para o momento atual.
         * @return A nova instância de processo em execução.
         */
        public ProcessInstanceExecution build() {
            ProcessInstanceExecution instance = new ProcessInstanceExecution();
            instance.setBusinessKey(this.businessKey);
            instance.setProcessDefinitionId(this.processDefinitionId);
            instance.setVariables(this.variables);
            instance.setStatus(ProcessInstanceStatus.ACTIVE);
            instance.setStartedAt(Instant.now());
            instance.setBusinessValue(this.businessValue);
            instance.setTenantId(this.tenantId);
            return instance;
        }
    }
}
