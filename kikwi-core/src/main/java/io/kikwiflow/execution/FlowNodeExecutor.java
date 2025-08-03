package io.kikwiflow.execution;

import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.ProcessInstance;

public class FlowNodeExecutor {
    private final TaskExecutor taskExecutor;

    public FlowNodeExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void execute(FlowNode currentNode, ProcessInstance instance) {
        //TODO
        //think in history!!!!
        throw new RuntimeException();
    }
}
