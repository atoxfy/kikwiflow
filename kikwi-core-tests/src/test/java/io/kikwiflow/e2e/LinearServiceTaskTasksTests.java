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

package io.kikwiflow.e2e;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.assertion.AssertableKikwiEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.execution.api.ExecutionContext;
import io.kikwiflow.execution.delegate.AddVariableDelegate;
import io.kikwiflow.execution.delegate.RemoveVariableDelegate;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LinearServiceTaskTasksTests {

    private final KikwiflowEngine kikwiflowEngine;
    private final AssertableKikwiEngine assertableKikwiEngine;
    private final TestDelegateResolver delegateResolver;
    private final KikwiflowConfig kikwiflowConfig;
    private final AddVariableDelegate addVariableDelegate;
    private final RemoveVariableDelegate removeVariableDelegate;

    private KikwiflowConfig getAndInitConfig(){
        KikwiflowConfig kikwiflowConfig1 = new KikwiflowConfig();
        kikwiflowConfig1.statsEnabled();
        kikwiflowConfig1.outboxEventsEnabled();
        return kikwiflowConfig1;
    }

    public LinearServiceTaskTasksTests(){
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.kikwiflowConfig = getAndInitConfig();
        this.delegateResolver = new TestDelegateResolver();
        this.addVariableDelegate = spy(new AddVariableDelegate());
        this.delegateResolver.register("addVariableDelegate", addVariableDelegate);
        this.removeVariableDelegate =  spy(new RemoveVariableDelegate());
        this.delegateResolver.register("removeVariableDelegate", removeVariableDelegate);
        this.kikwiflowEngine = new KikwiflowEngine(assertableKikwiEngine, kikwiflowConfig, delegateResolver, Collections.emptyList());
    }


    @AfterEach
    public void resetEngine(){
        assertableKikwiEngine.reset();
    }


    @Test
    void souldDeployAndExecuteSimpleSyncProcessWithServiceTasks() throws Exception {
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");

        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);
        String processDefinitionKey = processDefinition.key();
        String businessKey = "anyBusinessKey";
        Map<String, Object> startVariables = new HashMap<>();
        String initialVar = UUID.randomUUID().toString();
        String initialVarKey = "myVar";
        startVariables.put(initialVarKey, initialVar);

        // Configure spies to perform assertions at the time of invocation
        doAnswer(invocation -> {
            ExecutionContext context = invocation.getArgument(0);
            assertFalse(context.hasVariable("food"), "The 'food' variable should not exist when addVariableDelegate is called.");
            invocation.callRealMethod(); // Proceed with the actual delegate logic
            return null;
        }).when(addVariableDelegate).execute(any(ExecutionContext.class));

        doAnswer(invocation -> {
            ExecutionContext context = invocation.getArgument(0);
            assertTrue(context.hasVariable("food"), "The 'food' variable should exist when removeVariableDelegate is called.");
            assertEquals("cheeseburger", context.getVariable("food"));
            invocation.callRealMethod(); // Proceed with the actual delegate logic
            return null;
        }).when(removeVariableDelegate).execute(any(ExecutionContext.class));

        ProcessInstance processInstance = kikwiflowEngine.startProcess()
                .byKey(processDefinitionKey)
                .withBusinessKey(businessKey)
                .withVariables(startVariables)
                .execute();

        // Now, just verify that the methods were called, since the assertions were already done by doAnswer.
        verify(addVariableDelegate, times(1)).execute(any(ExecutionContext.class));
        verify(removeVariableDelegate, times(1)).execute(any(ExecutionContext.class));

        assertNotNull(processInstance.id());
        assertEquals(businessKey, processInstance.businessKey());
        assertEquals(processDefinition.id(), processInstance.processDefinitionId());
        assertEquals(ProcessInstanceStatus.COMPLETED, processInstance.status());
        assertEquals(initialVar, processInstance.variables().get(initialVarKey));

        assertableKikwiEngine.assertThatProcessInstanceNotExistsInRuntimeContext(processInstance.id());
        assertableKikwiEngine.evaluateEvents();

        assertableKikwiEngine.assertIfHasProcessInstanceInHistory(processInstance);
    }
}
