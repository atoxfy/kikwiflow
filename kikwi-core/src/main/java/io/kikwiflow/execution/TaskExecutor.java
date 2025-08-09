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
package io.kikwiflow.execution;

import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.execution.JavaDelegate;
import io.kikwiflow.exception.BadDefinitionExecutionException;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.bpmn.elements.task.ServiceTask;

import java.util.Objects;

public class TaskExecutor {
    private final DelegateResolver delegateResolver;

    public TaskExecutor(DelegateResolver delegateResolver) {
        this.delegateResolver = delegateResolver;
    }

    private boolean isExecutableByDelegate(ServiceTask serviceTask){
        return Objects.nonNull(serviceTask.getDelegateExpression());
    }

    public void execute(ExecutionContext executionContext){
        FlowNode executableTask = executionContext.getFlowNode();

        if(executableTask instanceof ServiceTask){
            ServiceTask serviceTask = (ServiceTask) executableTask;
            if(isExecutableByDelegate(serviceTask)){
                String delegateExpression = serviceTask.getDelegateExpression();
                String beanName = delegateExpression.replace("${", "").replace("}", "");
                JavaDelegate delegate = delegateResolver.resolve(beanName)
                        .orElseThrow(() -> new BadDefinitionExecutionException("JavaDelegate not found with name: " + beanName));

                try {
                    delegate.execute(executionContext);
                }catch (Exception e){
                    //todo handle errors
                    throw  e;
                }

            }else {
                throw new BadDefinitionExecutionException("Invalid execution method for task " + serviceTask.getId());
            }

         }
    }
}
