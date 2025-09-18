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

import java.math.BigDecimal;
import java.util.Map;

/**
 * Atua como uma fábrica estática para criar instâncias de processo em memória.
 * <p>
 * Esta classe utilitária é responsável por construir o objeto {@link ProcessInstanceExecution} inicial,
 * que representa o estado "quente" e mutável de um processo antes de ser persistido
 * e executado pela engine.
 */
public final class ProcessInstanceExecutionFactory {

    private ProcessInstanceExecutionFactory() {
        // Classe utilitária, não deve ser instanciada.
    }

    public static ProcessInstanceExecution create(String businessKey, String processDefinitionId, Map<String, ProcessVariable> variables, BigDecimal businessValue, String tenantId){
        return ProcessInstanceExecution.builder()
                .businessKey(businessKey)
                .processDefinitionId(processDefinitionId)
                .variables(variables)
                .businessValue(businessValue)
                .tenantId(tenantId)
                .build();
    }
}
