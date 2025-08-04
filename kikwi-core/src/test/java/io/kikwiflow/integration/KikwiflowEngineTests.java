package io.kikwiflow.integration;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.TestDelegateResolver;
import io.kikwiflow.execution.delegate.AddVariableDelegate;
import io.kikwiflow.execution.delegate.RemoveVariableDelegate;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.persistence.KikwiflowEngineInMemoryRepository;
import io.kikwiflow.persistence.KikwiflowEngineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KikwiflowEngineTests {
    private final KikwiflowEngine kikwiflowEngine;
    private final KikwiflowEngineInMemoryRepository kikwiflowEngineRepository;
    private final TestDelegateResolver delegateResolver;
    private final KikwiflowConfig kikwiflowConfig;
    private final AddVariableDelegate addVariableDelegate;
    private final RemoveVariableDelegate removeVariableDelegate;


    public KikwiflowEngineTests(){
        this.kikwiflowEngineRepository = new KikwiflowEngineInMemoryRepository();
        this.kikwiflowConfig = new KikwiflowConfig();

        this.delegateResolver = new TestDelegateResolver();
        this.addVariableDelegate = new AddVariableDelegate();
        this.delegateResolver.register("addVariableDelegate", addVariableDelegate);

        this.removeVariableDelegate = new RemoveVariableDelegate();
        this.delegateResolver.register("removeVariableDelegate", removeVariableDelegate);

        this.kikwiflowEngine = new KikwiflowEngine(kikwiflowEngineRepository, kikwiflowConfig, delegateResolver);
    }


    @AfterEach
    public void resetEngine(){
        kikwiflowEngineRepository.reset();
    }


    @Test
    void souldDeployAndExecuteSimpleSyncProcessWithStats() throws Exception {
        InputStream bpmnStream = getClass().getClassLoader().getResourceAsStream("sample.bpmn");

        ProcessDefinition processDefinition = kikwiflowEngine.deployDefinition(bpmnStream);
        String processDefinitionKey = processDefinition.getKey();
        String businessKey = "anyBusinessKey";
        Map<String, Object> startVariables = new HashMap<>();

        ProcessInstance processInstance = kikwiflowEngine.startProcessByKey(processDefinitionKey, businessKey, startVariables);

        //Check if all tasks done
        //Check if processInstance is done
        //Ckeck if delegates Are completed (mock or spy)?
    }
}
