package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.event.LightweightEvent;
import io.kikwiflow.execution.dto.UnitOfWorkResult;
import io.kikwiflow.persistence.api.data.event.CriticalEvent;
import io.kikwiflow.persistence.api.data.event.FlowNodeExecuted;
import io.kikwiflow.execution.dto.FlowNodeExecutionResult;
import io.kikwiflow.execution.dto.StartableProcessRecord;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.execution.ExecutableTask;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.persistence.api.data.event.OutboxEventEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class FlowNodeExecutor {

    private final TaskExecutor taskExecutor;
    private final Navigator navigator;
    private final KikwiflowConfig kikwiflowConfig;

    public FlowNodeExecutor(TaskExecutor taskExecutor, Navigator navigator, KikwiflowConfig kikwiflowConfig) {
        this.taskExecutor = taskExecutor;
        this.navigator = navigator;
        this.kikwiflowConfig = kikwiflowConfig;
    }

    private class FlowNodeExecution{
        private FlowNodeDefinitionSnapshot flowNodeDefinition;
        private ProcessDefinitionSnapshot processDefinitionSnapshot;
        private ProcessInstance processInstance;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;
        private Continuation continuation;
        private Supplier<Exception> errorSupplier;

        public FlowNodeExecution(){
        }

        public FlowNodeExecution flowNode(FlowNodeDefinitionSnapshot flowNodeDefinition){
            this.flowNodeDefinition = flowNodeDefinition;
            return this;
        }

        public FlowNodeExecution processInstance(ProcessInstance processInstance){
            this.processInstance = processInstance;
            return this;
        }

        public FlowNodeExecution processDefinition(ProcessDefinitionSnapshot processDefinitionSnapshot){
            this.processDefinitionSnapshot = processDefinitionSnapshot;
            return this;
        }

        public FlowNodeExecution onError(Supplier<Exception> supplier){
            this.errorSupplier = supplier;
            return this;
        }

        public FlowNodeExecutionResult execute(){

            try{
                this.startedAt = Instant.now();
                this.continuation = executeAndGetContinuation(flowNodeDefinition, processInstance, processDefinitionSnapshot);
                this.nodeExecutionStatus = NodeExecutionStatus.SUCCESS;
            }catch (Exception e){
                this.nodeExecutionStatus = NodeExecutionStatus.ERROR;
                if(Objects.nonNull(this.errorSupplier)){
                    this.errorSupplier.get();
                }

                throw e;
            }

            this.finishedAt = Instant.now();

            //todo
            final FlowNodeExecutionSnapshot flowNodeExecutionSnapshot = kikwiflowConfig.isStatsEnabled()
                    || kikwiflowConfig.isOutboxEventsEnabled() ? FlowNodeExecutionSnapshot.builder()
                    .flowNodeDefinition(flowNodeDefinition)
                    .processDefinitionSnapshot(processDefinitionSnapshot)
                    .processInstanceSnapshot(ProcessInstanceMapper.takeSnapshot(processInstance))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .nodeExecutionStatus(nodeExecutionStatus)
                    .build() : null;

            return new FlowNodeExecutionResult(flowNodeExecutionSnapshot, continuation);
        }
    }

    public UnitOfWorkResult runWhileNotFindAStopPoint(FlowNodeDefinitionSnapshot startPoint, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        FlowNodeDefinitionSnapshot currentNode = startPoint;

        List<OutboxEventEntity> criticalEvents = new ArrayList<>();
        boolean anyWorkWasDone = false;

        while (currentNode != null && !isWaitState(currentNode) && !isCommitBefore(currentNode)) {
            anyWorkWasDone = true;
            FlowNodeExecutionResult flowNodeExecutionResult = new FlowNodeExecution()
                    .processInstance(processInstance)
                    .flowNode(currentNode)
                    .processDefinition(processDefinition)
                    .execute();

            Continuation continuation = flowNodeExecutionResult.continuation();

            if(kikwiflowConfig.isStatsEnabled()){
                FlowNodeExecutionSnapshot flowNodeExecutionSnapshot = flowNodeExecutionResult.flowNodeExecutionSnapshot();
                FlowNodeExecuted flowNodeExecuted = new FlowNodeExecuted();
                flowNodeExecuted.setFlowNodeDefinitionId(flowNodeExecutionSnapshot.flowNodeDefinition().id());
                flowNodeExecuted.setProcessInstanceId(flowNodeExecutionSnapshot.processInstanceSnapshot().id());
                flowNodeExecuted.setProcessDefinitionId(flowNodeExecutionSnapshot.processDefinitionSnapshot().id());
                flowNodeExecuted.setNodeExecutionStatus(flowNodeExecutionSnapshot.nodeExecutionStatus());
                flowNodeExecuted.setStartedAt(flowNodeExecutionSnapshot.startedAt());
                flowNodeExecuted.setFinishedAt(flowNodeExecutionSnapshot.finishedAt());
                OutboxEventEntity outboxEvent = new OutboxEventEntity(flowNodeExecuted);
                criticalEvents.add(outboxEvent);
            }


            if (continuation == null || continuation.isAsynchronous()) {
                return new UnitOfWorkResult( new UnitOfWork(ProcessInstanceMapper.mapToEntity(processInstance),
                        List.of(),
                        List.of(),
                        criticalEvents),
                        continuation);
            } else {
                //todo aqui devemos ver futuramente para os casos de processamento paralelo
                currentNode = continuation.nextNodes().get(0);
            }
        }


        // Se saímos do loop, ou o processo terminou (currentNode == null) ou
        // encontrámos um ponto de paragem (wait state ou commit before).
        if (currentNode != null) {
            return new UnitOfWorkResult(
                    new UnitOfWork(ProcessInstanceMapper.mapToEntity(processInstance),
                            List.of(),
                            List.of(),
                            criticalEvents),
                    new Continuation(List.of(currentNode), true));
        }

        return null;
    }

    private void commitExecutionPath(UnitOfWork unitOfWork){
      //  processInstanceManager.update(unitOfWork.instanceToUpdate());
        if(kikwiflowConfig.isStatsEnabled()){
          //  asynchronousEventPublisher.publishEvents(lightweightEvents);
        }
    }

    private void execute(ExecutionContext executionContext) {
        FlowNodeDefinitionSnapshot flowNodeDefinition = executionContext.getFlowNode();
        if(flowNodeDefinition instanceof ExecutableTask){
            taskExecutor.execute(executionContext);
        }
        //TODO
        //think in history!!!!
    }



    private Continuation executeAndGetContinuation(FlowNodeDefinitionSnapshot flowNodeDefinition, ProcessInstance processInstance, ProcessDefinitionSnapshot processDefinition){
        // O ExecutionContext agora é criado com a instância mutável, permitindo que os delegates alterem seu estado.
        ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNodeDefinition);
        execute(executionContext);

        boolean isCommitAfter = isCommitAfter(flowNodeDefinition);
        return navigator.determineNextContinuation(flowNodeDefinition, processDefinition, isCommitAfter);
    }

    private boolean isCommitAfter(FlowNodeDefinitionSnapshot flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitAfter());
    }

    private boolean isWaitState(FlowNodeDefinitionSnapshot flowNodeDefinition){
        //TODO
        return false;
    }

    private boolean isCommitBefore(FlowNodeDefinitionSnapshot flowNodeDefinition){
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore());
    }
}
