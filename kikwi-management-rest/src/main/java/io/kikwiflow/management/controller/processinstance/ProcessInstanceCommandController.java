/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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

package io.kikwiflow.management.controller.processinstance;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.api.dto.ProcessInstanceStartRequest;
import io.kikwiflow.api.dto.SetVariablesRequest;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.spring.rest.api.command.ProcessInstanceOperationsRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(KikwiflowEngine.class)
public class ProcessInstanceCommandController implements ProcessInstanceOperationsRestApi {

    private final KikwiflowEngine engine;

    public ProcessInstanceCommandController(KikwiflowEngine engine) {
        this.engine = engine;
    }


    @Override
    public ProcessInstance start(ProcessInstanceStartRequest processInstanceStartRequest) {
        return engine.startProcess()
                .byKey(processInstanceStartRequest.processDefinitionKey())
                .targetFlowNodeId(processInstanceStartRequest.targetFlowNodeId())
                .from(processInstanceStartRequest.origin())
                .onTenant(processInstanceStartRequest.tenant())
                .withBusinessKey(processInstanceStartRequest.businessKey())
                .withVariables(processInstanceStartRequest.variables())
                .withBusinessValue(processInstanceStartRequest.businessValue())
                .execute();
    }

    @Override
    public ProcessInstance setVariables(String id, SetVariablesRequest setVariablesRequest) {
        return engine.setVariables(id, setVariablesRequest.variables());
    }

    @Override
    public void deleteInstance(String processInstanceId) {
        engine.deleteInstance(processInstanceId);
    }
}
