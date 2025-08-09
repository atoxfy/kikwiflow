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
package io.kikwiflow.execution.mapper;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.deploy.ProcessDefinitionDeploy;

public final class ProcessDefinitionMapper {

    private ProcessDefinitionMapper() {
        // Utility class
    }

    public static ProcessDefinitionSnapshot toSnapshot(final ProcessDefinition definition) {
        return new ProcessDefinitionSnapshot(
            definition.getId(), definition.getVersion(), definition.getKey(),
            definition.getName(), definition.getFlowNodes(), definition.getDefaultStartPoint()
        );
    }

    public static  ProcessDefinition mapToEntity(ProcessDefinitionDeploy processDefinitionDeploy){
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setKey(processDefinitionDeploy.getKey());
        processDefinition.setName(processDefinitionDeploy.getName());
        processDefinition.setFlowNodes(processDefinitionDeploy.getFlowNodes());
        processDefinition.setDefaultStartPoint(processDefinitionDeploy.getDefaultStartPoint());
        return processDefinition;
    }
}