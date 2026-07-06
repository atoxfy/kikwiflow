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

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.execution.api.context.ExecutionContext;
import io.kikwiflow.execution.api.handler.TaskHandler;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.node.Executable;


public class FlowNodeExecutor {

    private final TaskExecutor taskExecutor;

    /**
     * Constrói uma nova instância do FlowNodeExecutor.
     *
     * @param taskExecutor O executor responsável por invocar a lógica de negócio de uma tarefa (ex: {@link TaskHandler}).
     */
    public FlowNodeExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    /**
     * Executa a lógica de negócio associada a um nó, se aplicável.
     * <p>
     * Delega a execução para o {@link TaskExecutor} se o nó for do tipo {@link Executable}.
     * Para outros tipos de nós (como eventos), este método não faz nada.
     *
     * @param processInstance A instância de processo em execução.
     * @param processDefinition A definição do processo correspondente.
     * @param flowNodeDefinition O nó de fluxo específico a ser executado.
     */
    public void execute(ProcessInstanceExecution processInstance, ProcessDefinition processDefinition, FlowNodeDefinition flowNodeDefinition) {
        if (flowNodeDefinition instanceof Executable) {
            ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNodeDefinition);
            taskExecutor.execute(executionContext);
        }
    }
}
