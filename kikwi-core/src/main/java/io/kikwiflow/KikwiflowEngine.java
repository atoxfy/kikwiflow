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

import io.kikwiflow.bpmn.BpmnParser;
import io.kikwiflow.bpmn.impl.DefaultBpmnParser;
import io.kikwiflow.exception.ProcessDefinitionNotFoundException;
import io.kikwiflow.execution.ProcessInstanceManager;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import   io.kikwiflow.navigation.ProcessDefinitionManager;
import io.kikwiflow.persistence.ProcessExecutionRepository;
import io.kikwiflow.persistence.ProcessExecutionRepositoryImpl;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

public class KikwiflowEngine {

    private final ProcessExecutionRepository processExecutionRepository;
    private final ProcessDefinitionManager processDefinitionManager;
    private final ProcessInstanceManager processInstanceManager;

    public KikwiflowEngine(){
        this.processExecutionRepository = new ProcessExecutionRepositoryImpl(); //just for test
        this.processInstanceManager = new ProcessInstanceManager(processExecutionRepository);

        //Create more parsers and allow other flow definitions?
        final BpmnParser bpmnParser = new DefaultBpmnParser();
        this.processDefinitionManager = new ProcessDefinitionManager(bpmnParser, processExecutionRepository);
    }

    public void deployDefinition(InputStream is) throws Exception {
        processDefinitionManager.deploy(is);
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

        return processInstance;
    }
}
