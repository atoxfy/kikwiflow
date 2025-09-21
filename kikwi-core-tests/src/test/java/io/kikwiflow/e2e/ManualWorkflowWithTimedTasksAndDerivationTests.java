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
import io.kikwiflow.event.ExecutionEventListener;
import io.kikwiflow.execution.ProcessExecutionManager;
import io.kikwiflow.execution.TestDecisionRuleResolver;
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.execution.api.JavaDelegate;
import io.kikwiflow.factory.SingletonsFactory;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ProcessVariableVisibility;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.navigation.ProcessDefinitionService;
import io.kikwiflow.rule.api.DecisionRule;
import io.kikwiflow.view.adapter.WorkflowAdapter;
import io.kikwiflow.view.model.manual.Workflow;
import io.kikwiflow.view.model.manual.WorkflowStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.spy;

public class ManualWorkflowWithTimedTasksAndDerivationTests {

    private KikwiflowEngine kikwiflowEngine;
    private AssertableKikwiEngine assertableKikwiEngine;
    private TestDelegateResolver delegateResolver;
    private JavaDelegate fooServiceTaskDelegate;
    private TestDecisionRuleResolver decisionRuleResolver;
    private DecisionRule isExceptionFlowRule;
    private final String EXCEPTION_FLOW_RULE_NAME = "isExceptionFlow";
    private final String EXPECTED_VARIABLE_ON_EXCEPTIONAL_FLOW = "variable-1";
    private final String EXPECTED_VARIABLE_VALUE_ON_EXCEPTIONAL_FLOW = "DONE";


    @BeforeEach
    void setUp() {
        this.assertableKikwiEngine = new AssertableKikwiEngine();
        this.delegateResolver = new TestDelegateResolver();
        this.decisionRuleResolver = new TestDecisionRuleResolver();

        fooServiceTaskDelegate = spy(new TestJavaDelegate(context -> {
            context.setVariable(EXPECTED_VARIABLE_ON_EXCEPTIONAL_FLOW, new ProcessVariable(EXPECTED_VARIABLE_ON_EXCEPTIONAL_FLOW, ProcessVariableVisibility.PUBLIC, null, EXPECTED_VARIABLE_VALUE_ON_EXCEPTIONAL_FLOW));
        }));

        delegateResolver.register("fooServiceTaskDelegate", fooServiceTaskDelegate);
        KikwiflowConfig kikwiflowConfig = new KikwiflowConfig();

        isExceptionFlowRule = spy(new TestDecisionRule());
        this.decisionRuleResolver.register(EXCEPTION_FLOW_RULE_NAME, isExceptionFlowRule);

        ProcessDefinitionService processDefinitionService = SingletonsFactory.processDefinitionService(SingletonsFactory.bpmnParser(), assertableKikwiEngine,  SingletonsFactory.deployValidator(delegateResolver, decisionRuleResolver));
        Navigator navigator = SingletonsFactory.navigator(decisionRuleResolver);
        ProcessExecutionManager processExecutionManager = SingletonsFactory.processExecutionManager(delegateResolver, navigator, kikwiflowConfig);
        List<ExecutionEventListener> executionEventListeners = null;
        this.kikwiflowEngine = new KikwiflowEngine(processDefinitionService, navigator, processExecutionManager, assertableKikwiEngine, kikwiflowConfig, executionEventListeners);
    }

    @AfterEach
    public void resetEngine() {
        assertableKikwiEngine.reset();
    }

    private static class TestJavaDelegate implements JavaDelegate {
        private final java.util.function.Consumer<io.kikwiflow.execution.api.ExecutionContext> logic;

        public TestJavaDelegate(java.util.function.Consumer<io.kikwiflow.execution.api.ExecutionContext> logic) {
            this.logic = logic;
        }

        @Override
        public void execute(io.kikwiflow.execution.api.ExecutionContext execution) {
            if (logic != null) {
                logic.accept(execution);
            }
        }
    }


    private static class TestDecisionRule implements DecisionRule {

        @Override
        public boolean evaluate(Map<String, ProcessVariable> variables) {
            return true;
        }
    }

    @Test
    public void whenDeployAndGetWorkflow() throws Exception {
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("manual-workflow-with-timed-tasks-and-derivation.bpmn");
        ProcessDefinition definition = kikwiflowEngine.deployDefinition(bpmnStream);

        Workflow workflow = WorkflowAdapter.toManualWorkflow(definition);

        assertNotNull(workflow, "O workflow não deveria ser nulo.");
        assertEquals("manual-workflow-with-timed-tasks-and-derivation", workflow.key());
        assertEquals("manual-workflow-with-timed-tasks-and-derivation", workflow.name());
        assertNotNull(workflow.stages(), "A lista de estágios não deveria ser nula.");
        assertEquals(3, workflow.stages().size(), "Deveria haver 3 estágios (tarefas humanas) no fluxo.");

        WorkflowStage stage1 = workflow.stages().get(0);
        assertEquals("EXTERNAL_TASK_1", stage1.id());
        assertEquals("EXTERNAL_TASK_1", stage1.name());
        assertEquals(Collections.singletonList("EXTERNAL_TASK_2"), stage1.outgoing());
        assertEquals("128, 128, 128", stage1.additionalProperties().get("color"));

        WorkflowStage stage2 = workflow.stages().get(1);
        assertEquals("EXTERNAL_TASK_2", stage2.id());
        assertEquals("EXTERNAL_TASK_2", stage2.name());
        assertEquals(Collections.singletonList("EXTERNAL_TASK_3"), stage2.outgoing());
        assertEquals("PT1D", stage2.additionalProperties().get("sla"));

        WorkflowStage stage3 = workflow.stages().get(2);
        assertEquals("EXTERNAL_TASK_3", stage3.id());
        assertEquals("EXTERNAL_TASK_3", stage3.name());
        assertNull(stage3.outgoing(), "O último estágio não deveria ter saídas.");
    }
}
