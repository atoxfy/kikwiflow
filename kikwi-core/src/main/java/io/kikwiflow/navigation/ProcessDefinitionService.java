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
package io.kikwiflow.navigation;

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.cache.ProcessDefinitionCache;
import io.kikwiflow.exception.ProcessDefinitionNotFoundException;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.validation.DeployValidator;

import java.io.InputStream;
import java.util.Optional;

/**
 * Gerencia o ciclo de vida das definições de processo ({@link ProcessDefinition}).
 * <p>
 * Esta classe atua como um serviço central para implantar (deploy) novas definições a partir de arquivos BPMN
 * e para recuperá-las para execução.
 * Utiliza uma camada de cache ({@link ProcessDefinitionCache}) para otimizar o acesso a definições
 * frequentemente utilizadas.
 */
public class ProcessDefinitionService {
    private final BpmnParser bpmnParser;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final ProcessDefinitionCache processDefinitionCache;
    private final DeployValidator deployValidator;

    /**
     * Constrói uma nova instância do ProcessDefinitionService.
     *
     * @param bpmnParser O parser responsável por ler um arquivo BPMN e transformá-lo num objeto {@link ProcessDefinition}.
     * @param kikwiEngineRepository O repositório para persistir e buscar as definições de processo.
     */
    public ProcessDefinitionService(BpmnParser bpmnParser, KikwiEngineRepository kikwiEngineRepository, DeployValidator deployValidator){
        this.bpmnParser =  bpmnParser;
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.deployValidator = deployValidator;
        this.processDefinitionCache = new ProcessDefinitionCache();
    }

    /**
     * Implanta (deploy) uma nova definição de processo a partir de um stream de arquivo BPMN.
     * O processo envolve:
     * <ol>
     *   <li>Fazer o parse do arquivo XML.</li>
     *   <li>Transformá-lo num objeto {@link ProcessDefinition}.</li>
     *   <li>Persistir a nova definição no repositório.</li>
     * </ol>
     *
     * @param inputStream O stream do arquivo BPMN a ser implantado.
     * @return A {@link ProcessDefinition} persistida, incluindo o seu ID e versão.
     * @throws Exception se ocorrer um erro durante o parse ou a persistência.
     */
    public ProcessDefinition deploy(InputStream inputStream) throws Exception {
        ProcessDefinition processDefinitionDeploy = bpmnParser.parse(inputStream);
        deployValidator.validate(processDefinitionDeploy);
        ProcessDefinition processDefinition =  kikwiEngineRepository.saveProcessDefinition(processDefinitionDeploy);
        processDefinitionCache.clear();;
        return processDefinition;
    }

    /**
     * Obtém uma definição de processo pela sua chave (key), utilizando uma estratégia de cache.
     * <p>
     * Primeiro, tenta encontrar a definição no cache em memória. Se não encontrar (cache miss),
     * busca no repositório, e se encontrar, armazena no cache para futuras consultas.
     *
     * @param processDefinitionKey A chave única da definição do processo (o atributo 'id' do elemento 'process' no BPMN).
     * @return Um {@link Optional} contendo a {@link ProcessDefinition} se encontrada, ou vazio caso contrário.
     */
    public Optional<ProcessDefinition> getByKey(String processDefinitionKey){
        return processDefinitionCache.findByKey(processDefinitionKey)
                .or(() -> getAndLoadOnCacheByKey(processDefinitionKey));
    }

    public Optional<ProcessDefinition> getById(String processDefinitionKey){
        return processDefinitionCache.findById(processDefinitionKey)
                .or(() -> getAndLoadOnCacheById(processDefinitionKey));
    }


    /**
     * Obtém uma definição de processo pela sua chave, ou lança uma exceção se não for encontrada.
     * É um método de conveniência que envolve o {@link #getByKey(String)}.
     *
     * @param processDefinitionKey A chave única da definição do processo.
     * @return A {@link ProcessDefinition} encontrada.
     * @throws ProcessDefinitionNotFoundException se nenhuma definição com a chave fornecida for encontrada.
     */
    public ProcessDefinition getByKeyOrElseThrow(String processDefinitionKey){
        return getByKey(processDefinitionKey)
                .orElseThrow(() -> new ProcessDefinitionNotFoundException("ProcessDefinition not found with key: " + processDefinitionKey));
    }

    /**
     * Busca uma definição no repositório e, se encontrada, a carrega no cache.
     * Este é um método auxiliar para a lógica de 'cache-aside' implementada em {@link #getByKey(String)}.
     *
     * @param processDefinitionKey A chave da definição a ser buscada.
     * @return Um {@link Optional} contendo a definição, se encontrada no repositório.
     */
    private Optional<ProcessDefinition> getAndLoadOnCacheByKey(String processDefinitionKey){
        return kikwiEngineRepository.findProcessDefinitionByKey(processDefinitionKey)
                .map(processDefinitionCache::add);
    }

    private Optional<ProcessDefinition> getAndLoadOnCacheById(String processDefinitionId){
        return kikwiEngineRepository.findProcessDefinitionById(processDefinitionId)
                .map(processDefinitionCache::add);
    }

    public void clearCache(){
        processDefinitionCache.clear();
    }
}
