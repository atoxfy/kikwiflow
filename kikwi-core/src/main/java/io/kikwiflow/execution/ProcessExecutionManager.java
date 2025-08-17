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

import io.kikwiflow.execution.dto.StartableProcessRecord;
import io.kikwiflow.execution.dto.ExecutionResult;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;

/**
 * Orquestra a execução de uma instância de processo.
 * <p>
 * Esta classe atua como uma fachada (facade) para a lógica de execução,
 * recebendo uma instância de processo pronta para ser iniciada e delegando
 * a sua condução ao {@link FlowNodeExecutor}.
 * A sua principal responsabilidade é isolar o motor principal ({@link io.kikwiflow.KikwiflowEngine})
 * dos detalhes de como a execução do fluxo é iniciada.
 */
public class ProcessExecutionManager {

    private final FlowNodeExecutor flowNodeExecutor;

    /**
     * Constrói uma nova instância do ProcessExecutionManager.
     *
     * @param flowNodeExecutor O executor que efetivamente percorre o grafo do processo.
     */
    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor) {
        this.flowNodeExecutor = flowNodeExecutor;
    }

    /**
     * Inicia a execução de uma nova instância de processo.
     * <p>
     * Este método encontra o ponto de início padrão da definição do processo e
     * invoca o {@link FlowNodeExecutor} para começar a executar o fluxo.
     *
     * @param startableProcessRecord Um registro contendo a definição do processo e a instância
     *                               de processo a ser executada.
     * @return O {@link ExecutionResult} que contém o resultado da execução síncrona.
     */
    public ExecutionResult startProcessExecution(StartableProcessRecord startableProcessRecord){
        FlowNodeDefinition startPoint = startableProcessRecord.processDefinition().defaultStartPoint();
        return flowNodeExecutor.runWhileNotFindAStopPoint(startPoint, startableProcessRecord.processInstance(), startableProcessRecord.processDefinition());
    }
}
