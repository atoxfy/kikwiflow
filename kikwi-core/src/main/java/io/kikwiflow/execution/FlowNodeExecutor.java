package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.EventPublisher;
import io.kikwiflow.event.ExecutionEvent;
import io.kikwiflow.event.model.ElementCoveredEvent;
import io.kikwiflow.execution.dto.StartableProcessRecord;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.execution.Continuation;
import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.bpmn.elements.FlowNode;
import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.event.model.enumerated.CoveredElementStatus;
import io.kikwiflow.navigation.Navigator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FlowNodeExecutor {

    private final TaskExecutor taskExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;
    private final EventPublisher eventPublisher;
    private final ProcessInstanceManager processInstanceManager;

    public FlowNodeExecutor(TaskExecutor taskExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig, EventPublisher eventPublisher, ProcessInstanceManager processInstanceManager) {
        this.taskExecutor = taskExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
        this.eventPublisher = eventPublisher;
        this.processInstanceManager = processInstanceManager;
    }

    private Continuation runWhileNotFindAStopPoint(FlowNode startPoint, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        FlowNode currentNode = startPoint;

        List<ExecutionEvent> executionEvents = new ArrayList<>();
        while (currentNode != null && !isWaitState(currentNode) && !isCommitBefore(currentNode)) {

            Instant startedAt = Instant.now();
            CoveredElementStatus coveredElementStatus = null;
            Continuation continuation = null;

            try{
                continuation = executeAndGetContinuation(currentNode, processInstance, processDefinition);
                coveredElementStatus = CoveredElementStatus.SUCCESS;
            }catch (Exception e){
                coveredElementStatus = CoveredElementStatus.ERROR;
                throw e;
                //todo tratar erro
            }

            Instant finishedAt = Instant.now();
            if(kikwiflowConfig.isStatsEnabled()){
                ElementCoveredEvent coverageSnapshot =  ElementCoveredEvent.builder()
                        .processInstanceId(processInstance.getId())
                        .processDefinitionId(processDefinition.id())
                        .elementId(currentNode.getId())
                        .finishedAt(finishedAt)
                        .startedAt(startedAt)
                        .status(coveredElementStatus)
                        .build();

                executionEvents.add(coverageSnapshot);
            }


            if (continuation == null || continuation.isAsynchronous()) {
                // O processo terminou ou o próximo passo é assíncrono.
                return continuation;
            } else {
                //todo aqui devemos ver futuramente para os casos de processamento paralelo
                currentNode = continuation.getNextNodes().get(0);
            }
        }

        finalizePath(processInstance, executionEvents);

        // Se saímos do loop, ou o processo terminou (currentNode == null) ou
        // encontrámos um ponto de paragem (wait state ou commit before).
        if (currentNode != null) {
            return new Continuation(List.of(currentNode), true);
        }

        return null; // Processo terminou
    }

    private void finalizePath(ProcessInstance processInstance, List<ExecutionEvent> executionEvents){
        processInstanceManager.update(processInstance);
        if(kikwiflowConfig.isStatsEnabled()){
            eventPublisher.publishEvents(executionEvents);
        }
    }
    private void execute(ExecutionContext executionContext) {
        FlowNode flowNode = executionContext.getFlowNode();
        if(flowNode instanceof ExecutableTask){
            taskExecutor.execute(executionContext);
        }
        //TODO
        //think in history!!!!
    }

    public Continuation startProcessExecution(StartableProcessRecord startableProcessRecord){
        FlowNode startPoint = navigator.findStartPoint(startableProcessRecord.processDefinition());
        return runWhileNotFindAStopPoint(startPoint, startableProcessRecord.processInstance(), startableProcessRecord.processDefinition());
    }

    private Continuation executeAndGetContinuation(FlowNode flowNode, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        // O ExecutionContext agora é criado com a instância mutável, permitindo que os delegates alterem seu estado.
        ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNode);
        execute(executionContext);

        boolean isCommitAfter = isCommitAfter(flowNode);
        return navigator.determineNextContinuation(flowNode, processDefinition, isCommitAfter);
    }



    private boolean isCommitAfter(FlowNode flowNode) {
        return Boolean.TRUE.equals(flowNode.getCommitAfter());
    }

    private boolean isWaitState(FlowNode flowNode){
        //TODO
        return false;
    }

    private boolean isCommitBefore(FlowNode flowNode){
        return Boolean.TRUE.equals(flowNode.getCommitBefore());
    }
}
