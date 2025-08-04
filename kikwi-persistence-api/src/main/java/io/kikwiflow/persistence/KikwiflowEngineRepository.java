/*
 * Copyright Atoxfy and/or licensed to Atoxfy
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
package io.kikwiflow.persistence;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.task.ServiceTask;
import io.kikwiflow.model.deploy.ProcessDefinitionDeploy;
import io.kikwiflow.model.execution.ExecutableTaskEntity;
import io.kikwiflow.model.execution.ProcessInstance;

import java.util.Map;
import java.util.Optional;

/**
 * Fachada do repositório para todas as operações de persistência relacionadas
 * à execução de um processo.
 *
 * Esta interface abstrai a complexidade de interagir com as diversas coleções
 * (instâncias, tarefas executáveis, estados de espera, histórico).
 */
public interface KikwiflowEngineRepository {

    /**
     * Cria uma nova instância de processo na base de dados.
     *
     * @param instance O objeto ProcessInstance a ser persistido.
     */
    ProcessInstance save(ProcessInstance instance);

    /**
     * Encontra uma instância de processo pelo seu ID.
     *
     * @param processInstanceId O ID da instância a ser procurada.
     * @return Um Optional contendo a ProcessInstance se encontrada, ou vazio caso contrário.
     */
    Optional<ProcessInstance> findById(String processInstanceId);

    /**
     * Atualiza as variáveis de uma instância de processo existente.
     *
     * @param processInstanceId O ID da instância a ser atualizada.
     * @param variables O mapa de variáveis a serem adicionadas ou sobrescritas.
     */
    void updateVariables(String processInstanceId, Map<String, Object> variables);


    /**
     * Adiciona uma nova tarefa à fila de execução.
     *
     * @param task A tarefa executável a ser criada.
     */
    ExecutableTaskEntity create(ExecutableTaskEntity task);

    /**
     * Atomicamente encontra, bloqueia e retorna a próxima tarefa executável
     * da fila, pronta para ser processada.
     *
     * @return Um Optional contendo a próxima tarefa, ou vazio se a fila estiver vazia.
     */
    Optional<ExecutableTaskEntity> acquireNext();


    /**
     * Move uma tarefa executável concluída da fila de execução para o histórico.
     *
     * @param completedTask A tarefa que foi executada com sucesso.
     */
    void moveToHistory(ServiceTask completedTask);


    public ProcessDefinition save(ProcessDefinitionDeploy processDefinitionDeploy);

    public Optional<ProcessDefinition> findByKey(String processDefinitionKey);

    public void addToHistory(ProcessDefinition processDefinition);

}

