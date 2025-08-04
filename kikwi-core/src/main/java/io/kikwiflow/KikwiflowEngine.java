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
package io.kikwiflow;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.api.ExecutionContext;
import io.kikwiflow.api.JavaDelegate;
import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.exception.ProcessDefinitionNotFoundException;
import io.kikwiflow.execution.DelegateResolver;
import io.kikwiflow.execution.FlowNodeExecutor;
import io.kikwiflow.execution.ProcessInstanceManager;
import io.kikwiflow.execution.TaskExecutor;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.Continuation;
import io.kikwiflow.model.execution.CoverageSnapshot;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.CoveredElementStatus;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionManager;
import io.kikwiflow.persistence.KikwiflowEngineRepository;
import io.kikwiflow.persistence.StatsManager;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KikwiflowEngine {
    private final ProcessDefinitionManager processDefinitionManager;
    private final ProcessInstanceManager processInstanceManager;
    private final Navigator navigator;
    private final FlowNodeExecutor flowNodeExecutor;
    private final KikwiflowConfig kikwiflowConfig;

    private final StatsManager statsManager;

    public KikwiflowEngine(KikwiflowEngineRepository kikwiflowEngineRepository, KikwiflowConfig kikwiflowConfig, DelegateResolver delegateResolver){
        this.processInstanceManager = new ProcessInstanceManager(kikwiflowEngineRepository);
        this.flowNodeExecutor = new FlowNodeExecutor(new TaskExecutor(delegateResolver));

        //Create more parsers and allow other flow definitions?
        final BpmnParser bpmnParser = new DefaultBpmnParser();
        this.processDefinitionManager = new ProcessDefinitionManager(bpmnParser, kikwiflowEngineRepository);
        this.navigator = new Navigator(processDefinitionManager);
        this.kikwiflowConfig = kikwiflowConfig;//Just for test
        this.statsManager = new StatsManager(kikwiflowConfig);
    }

    public ProcessDefinition deployDefinition(InputStream is) throws Exception {
        return processDefinitionManager.deploy(is);
    }

    private ProcessDefinition getProcessDefinition(String processDefinitionKey){
        return processDefinitionManager.getByKey(processDefinitionKey)
                .orElseThrow(() -> new ProcessDefinitionNotFoundException("ProcessDefinition not found with key" + processDefinitionKey));
    }

    public ProcessInstance startProcessByKey(String processDefinitionKey, String businessKey, Map<String, Object> variables){
        if(Objects.isNull(businessKey)){
            throw new ProcessDefinitionNotFoundException("buisinessKey can't be null to start a process");
        }

        ProcessDefinition processDefinition = getProcessDefinition(processDefinitionKey);

        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setVariables(variables);
        processInstance.setProcessDefinitionId(processDefinition.getId());
        processInstance.setBusinessKey(businessKey);
        ProcessInstance startedProcessInstance = processInstanceManager.create(processInstance);

        //TODO identify first node and delegate it execution to executor.
        //if is an commit before task, simply save task on the database
        //or else the executor need to execute tasks while not found a wait state
        //its simple: if haven't a  wait state, this thread need to process it
        // else, store the wait state on database.

        FlowNode startPoint = navigator.findStartPoint(processDefinition);
        Continuation nextContinuation = runWhileNotFindAStopPoint(startPoint, processInstance, processDefinition);

        // Se o ciclo síncrono parou porque encontrou uma tarefa assíncrona,
        // 'nextContinuation' não será nulo.
        if (nextContinuation != null && nextContinuation.isAsynchronous()) {

            for (FlowNode asyncNode : nextContinuation.getNextNodes()) {
                //Criar as executable tasks (fazer em bulk)
            }
        }
        return processInstance;
    }

    private Continuation executeAndGetContinuation(FlowNode flowNode, ProcessInstance processInstance, ProcessDefinition processDefinition){

        Instant startedAt = Instant.now();
        // EXECUTA
        CoveredElementStatus coveredElementStatus = null;
        try{
            ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNode);
            flowNodeExecutor.execute(executionContext);
            coveredElementStatus = CoveredElementStatus.SUCCESS;
        }catch (Exception e){
            //GERENCIAR ERROS
            coveredElementStatus = CoveredElementStatus.ERROR;
        }


        Instant finishedAt = Instant.now();

        if(kikwiflowConfig.isStatsEnabled()){
            CoverageSnapshot coverageSnapshot = new CoverageSnapshot(flowNode, processDefinition, processInstance, startedAt, finishedAt, coveredElementStatus);
            statsManager.registerCoverage(coverageSnapshot);
        }

        //Deve parar?
        boolean isCommitAfter = isCommitAfter(flowNode);

        // Determinha proximo noodo
        Continuation continuation = navigator.determineNextContinuation(flowNode, processDefinition, isCommitAfter);
        return continuation;
    }

    private Continuation runWhileNotFindAStopPoint(FlowNode startPoint, ProcessInstance instance, ProcessDefinition processDefinition){
        FlowNode currentNode = startPoint;

        // O ciclo continua enquanto não encontrar ponto de parada
        while (currentNode != null && !isWaitState(currentNode) && !isCommitBefore(currentNode)) {
            Continuation continuation = executeAndGetContinuation(currentNode, instance, processDefinition);
            if (continuation == null || continuation.isAsynchronous()) {
                // O processo terminou ou o próximo passo é assíncrono.
                return continuation;
            } else {
                // O próximo passo também é síncrono, o loop continua.
                // (Assumindo um fluxo linear para este exemplo)
                currentNode = continuation.getNextNodes().get(0);
            }
        }

        // Se saímos do loop, ou o processo terminou (currentNode == null) ou
        // encontrámos um ponto de paragem (wait state ou commit before).
        if (currentNode != null) {
            return new Continuation(List.of(currentNode), true);
        }

        return null; // Processo terminou
    }

    private boolean isCommitAfter(FlowNode flowNode) {
        return flowNode.getCommitAfter();
    }

    private boolean isWaitState(FlowNode flowNode){
        //TODO
        return false;
    }

    private boolean isCommitBefore(FlowNode flowNode){
        return flowNode.getCommitBefore();
    }
}
