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
package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fachada do repositório para todas as operações de persistência relacionadas
 * à execução de um processo.
 *
 * Esta interface abstrai a complexidade de interagir com as diversas coleções
 * (instâncias, tarefas executáveis, estados de espera, histórico).
 */
public interface KikwiEngineRepository {

    /**
     * Cria uma nova instância de processo na base de dados.
     *
     * @param instance O objeto ProcessInstance a ser persistido.
     */
    ProcessInstance saveProcessInstance(ProcessInstance instance);

    /**
     * Encontra uma instância de processo pelo seu ID.
     *
     * @param processInstanceId O ID da instância a ser procurada.
     * @return Um Optional contendo a ProcessInstance se encontrada, ou vazio caso contrário.
     */
    Optional<ProcessInstance> findProcessInstanceById(String processInstanceId);

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
    ExecutableTask createExecutableTask(ExecutableTask task);

    ExternalTask createExternalTask(ExternalTask task);

    List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId);


    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy);

    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey);

    public ProcessInstance updateProcessInstance(ProcessInstance processInstance);//TODO just for tests now

    public void deleteProcessInstanceById(String processInstanceId);

    public void commitWork(UnitOfWork unitOfWork);
}
