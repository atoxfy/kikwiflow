package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.event.model.FlowNodeExecuted;
import io.kikwiflow.execution.dto.StartableProcessRecord;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.execution.Continuation;
import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.navigation.Navigator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FlowNodeExecutor {

    private final TaskExecutor taskExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;
    private final AsynchronousEventPublisher asynchronousEventPublisher;
    private final ProcessInstanceManager processInstanceManager;

    public FlowNodeExecutor(TaskExecutor taskExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig, AsynchronousEventPublisher asynchronousEventPublisher, ProcessInstanceManager processInstanceManager) {
        this.taskExecutor = taskExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
        this.asynchronousEventPublisher = asynchronousEventPublisher;
        this.processInstanceManager = processInstanceManager;
    }

    private Continuation runWhileNotFindAStopPoint(FlowNodeDefinition startPoint, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        FlowNodeDefinition currentNode = startPoint;

        List<ExecutionEvent> executionEvents = new ArrayList<>();
        while (currentNode != null && !isWaitState(currentNode) && !isCommitBefore(currentNode)) {

            Instant startedAt = Instant.now();
            NodeExecutionStatus nodeExecutionStatus = null;
            Continuation continuation = null;

            try{
                continuation = executeAndGetContinuation(currentNode, processInstance, processDefinition);
                nodeExecutionStatus = NodeExecutionStatus.SUCCESS;
            }catch (Exception e){
                nodeExecutionStatus = NodeExecutionStatus.ERROR;
                throw e;
                //todo tratar erro
            }

            Instant finishedAt = Instant.now();
            if(kikwiflowConfig.isStatsEnabled()){
                FlowNodeExecuted flowNodeExecutedEvent =  FlowNodeExecuted();
                executionEvents.add(flowNodeExecutedEvent);
            }


            if (continuation == null || continuation.isAsynchronous()) {
                // O processo terminou ou o próximo passo é assíncrono.
                return continuation;
            } else {
                //todo aqui devemos ver futuramente para os casos de processamento paralelo
                currentNode = continuation.getNextNodes().get(0);
            }
        }

        commitExecutionPath(processInstance, executionEvents);

        // Se saímos do loop, ou o processo terminou (currentNode == null) ou
        // encontrámos um ponto de paragem (wait state ou commit before).
        if (currentNode != null) {
            return new Continuation(List.of(currentNode), true);
        }

        return null; // Processo terminou
    }

    private void commitExecutionPath(ProcessInstance processInstance, List<ExecutionEvent> executionEvents){
        processInstanceManager.update(processInstance);
        if(kikwiflowConfig.isStatsEnabled()){
            asynchronousEventPublisher.publishEvents(executionEvents);
        }
    }
    private void execute(ExecutionContext executionContext) {
        FlowNodeDefinition flowNodeDefinition = executionContext.getFlowNode();
        if(flowNodeDefinition instanceof ExecutableTask){
            taskExecutor.execute(executionContext);
        }
        //TODO
        //think in history!!!!
    }

    public Continuation startProcessExecution(StartableProcessRecord startableProcessRecord){
        FlowNodeDefinition startPoint = navigator.findStartPoint(startableProcessRecord.processDefinition());
        return runWhileNotFindAStopPoint(startPoint, startableProcessRecord.processInstance(), startableProcessRecord.processDefinition());
    }

    private Continuation executeAndGetContinuation(FlowNodeDefinition flowNodeDefinition, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        // O ExecutionContext agora é criado com a instância mutável, permitindo que os delegates alterem seu estado.
        ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNodeDefinition);
        execute(executionContext);

        boolean isCommitAfter = isCommitAfter(flowNodeDefinition);
        return navigator.determineNextContinuation(flowNodeDefinition, processDefinition, isCommitAfter);
    }



    private boolean isCommitAfter(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.getCommitAfter());
    }

    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition){
        //TODO
        return false;
    }

    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition){
        return Boolean.TRUE.equals(flowNodeDefinition.getCommitBefore());
    }
}
