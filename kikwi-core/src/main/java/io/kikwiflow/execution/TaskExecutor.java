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

import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.api.JavaDelegate;
import io.kikwiflow.exception.BadDefinitionExecutionException;

import java.util.Objects;

/**
 * Responsável por executar a lógica de negócio associada a uma tarefa (task) do processo.
 * <p>
 * Esta classe atua como um despachante (dispatcher). Quando o motor encontra uma tarefa
 * que requer a execução de código Java (como uma {@link ServiceTaskDefinition}),
 * o {@code TaskExecutor} utiliza um {@link DelegateResolver} para encontrar e invocar
 * a implementação correta da {@link JavaDelegate}.
 */
public class TaskExecutor {

    private final DelegateResolver delegateResolver;

    /**
     * Constrói uma nova instância do TaskExecutor.
     *
     * @param delegateResolver O resolvedor que encontra a instância do bean {@link JavaDelegate}
     *                         com base no nome fornecido na definição do processo (ex: `${myBean}`).
     */
    public TaskExecutor(DelegateResolver delegateResolver) {
        this.delegateResolver = delegateResolver;
    }

    /**
     * Verifica se uma ServiceTask está configurada para ser executada por um JavaDelegate.
     *
     * @param serviceTask A definição da tarefa de serviço.
     * @return {@code true} se a tarefa possuir um `delegateExpression`, {@code false} caso contrário.
     */
    private boolean isExecutableByDelegate(ServiceTaskDefinition serviceTask){
        return Objects.nonNull(serviceTask.delegateExpression());
    }

    /**
     * Executa a lógica de negócio para o nó de fluxo fornecido no contexto de execução.
     * <p>
     * Atualmente, suporta a execução de {@link ServiceTaskDefinition} que possuem um `delegateExpression`.
     *
     * @param executionContext O contexto da execução atual, que contém a instância do processo,
     *                         variáveis e informações sobre o nó atual.
     * @throws BadDefinitionExecutionException se o delegate não for encontrado ou se a tarefa
     *                                         não estiver configurada com um método de execução válido.
     */
    public void execute(ExecutionContext executionContext){
        FlowNodeDefinition executableTask = executionContext.getFlowNode();

        if (executableTask instanceof ServiceTaskDefinition serviceTask) {
            if(isExecutableByDelegate(serviceTask)){
                String delegateExpression = serviceTask.delegateExpression();
                String beanName = delegateExpression.replace("${", "").replace("}", "");
                JavaDelegate delegate = delegateResolver.resolve(beanName)
                        .orElseThrow(() -> new BadDefinitionExecutionException("JavaDelegate not found with name: " + beanName));

                delegate.execute(executionContext);

            }else {
                throw new BadDefinitionExecutionException("Invalid execution method for task " + serviceTask.id());
            }
         }
    }
}
