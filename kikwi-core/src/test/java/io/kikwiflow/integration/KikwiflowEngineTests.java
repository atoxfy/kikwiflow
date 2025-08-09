package io.kikwiflow.integration;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.InMemoryHistoryEventListener;
import io.kikwiflow.event.model.ProcessInstanceFinishedEvent;
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.execution.delegate.AddVariableDelegate;
import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.execution.delegate.RemoveVariableDelegate;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSnapshot;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.persistence.InMemoryKikwiEngineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class KikwiflowEngineTests {

    private final KikwiflowEngine kikwiflowEngine;
    private final InMemoryKikwiEngineRepository kikwiflowEngineRepository;
    private final TestDelegateResolver delegateResolver;
    private final KikwiflowConfig kikwiflowConfig;
    private final AddVariableDelegate addVariableDelegate;
    private final RemoveVariableDelegate removeVariableDelegate;
    private final InMemoryHistoryEventListener inMemoryHistoryEventListener;

    private KikwiflowConfig getAndInitConfig(){
        KikwiflowConfig kikwiflowConfig1 = new KikwiflowConfig();
        kikwiflowConfig1.statsEnabled();
        return kikwiflowConfig1;
    }

    public KikwiflowEngineTests(){
        this.kikwiflowEngineRepository = new InMemoryKikwiEngineRepository();
        this.kikwiflowConfig = getAndInitConfig();
        this.kikwiflowConfig.statsEnabled();
        this.delegateResolver = new TestDelegateResolver();
        this.addVariableDelegate = spy(new AddVariableDelegate());
        this.delegateResolver.register("addVariableDelegate", addVariableDelegate);
        this.removeVariableDelegate =  spy(new RemoveVariableDelegate());
        this.delegateResolver.register("removeVariableDelegate", removeVariableDelegate);
        this.inMemoryHistoryEventListener = new InMemoryHistoryEventListener();
        this.kikwiflowEngine = new KikwiflowEngine(kikwiflowEngineRepository, kikwiflowConfig, delegateResolver, Collections.singletonList(inMemoryHistoryEventListener));
    }


    @AfterEach
    public void resetEngine(){
        kikwiflowEngineRepository.reset();
    }


    @Test
    void souldDeployAndExecuteSimpleSyncProcessWithStats() throws Exception {
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");

        ProcessDefinitionSnapshot processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);
        String processDefinitionKey = processDefinition.key();
        String businessKey = "anyBusinessKey";
        Map<String, Object> startVariables = new HashMap<>();
        String initialVar = UUID.randomUUID().toString();

        startVariables.put("myVar",initialVar);

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

        ProcessInstanceSnapshot processInstance = kikwiflowEngine.startProcess()
                .byKey(processDefinitionKey)
                .withBusinessKey(businessKey)
                .withVariables(startVariables)
                .execute();

        // Now, just verify that the methods were called, since the assertions were already done by doAnswer.
        verify(addVariableDelegate, times(1)).execute(any(ExecutionContext.class));
        verify(removeVariableDelegate, times(1)).execute(any(ExecutionContext.class));

        Optional<ProcessInstance> hotProcessInstanceOpt = kikwiflowEngineRepository.findProcessInstanceById(processInstance.id());
        assertFalse(hotProcessInstanceOpt.isPresent()); //Para um processo sincrono deve ter sido movido para o historico
        //pois é um dado frio agora


        //Busca dado frio do histórico
        Optional<ProcessInstanceFinishedEvent> coldProcessInstanceOpt = inMemoryHistoryEventListener.getProcessInstanceById(processInstance.id());
        assertTrue(coldProcessInstanceOpt.isPresent());

        ProcessInstanceFinishedEvent savedProcessInstance = coldProcessInstanceOpt.get();
        assertFalse(savedProcessInstance.variables().isEmpty());
        assertFalse(savedProcessInstance.variables().containsKey("food"));
        assertEquals(initialVar, savedProcessInstance.variables().get("myVar"));
        assertEquals(ProcessInstanceStatus.COMPLETED, savedProcessInstance.status());

    }
}
