package io.kikwiflow.execution;

import io.kikwiflow.api.ExecutionContext;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.model.execution.ProcessInstance;

public class FlowNodeExecutor {
    private final TaskExecutor taskExecutor;

    public FlowNodeExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void execute(ExecutionContext executionContext) {
        FlowNode flowNode = executionContext.getFlowNode();
        if(flowNode instanceof ExecutableTask){
            taskExecutor.execute(executionContext);
        }
        //TODO
        //think in history!!!!
        throw new RuntimeException();
    }
}
