package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.execution.dto.UnitOfWorkResult;
import io.kikwiflow.model.execution.node.WaitState;
import io.kikwiflow.model.event.FlowNodeExecuted;
import io.kikwiflow.execution.dto.FlowNodeExecutionResult;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.execution.mapper.ProcessInstanceMapper;
import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinition;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.node.Executable;
import io.kikwiflow.model.execution.FlowNodeExecutionSnapshot;
import io.kikwiflow.model.execution.enumerated.NodeExecutionStatus;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.model.event.OutboxEventEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
        private FlowNodeDefinition flowNodeDefinition;
        private ProcessDefinition processDefinition;
        private ProcessInstanceExecution processInstance;
        private Instant startedAt;
        private Instant finishedAt;
        private NodeExecutionStatus nodeExecutionStatus;
        private Continuation continuation;
        private Supplier<Exception> errorSupplier;

        public FlowNodeExecution(){
        }

        public FlowNodeExecution flowNode(FlowNodeDefinition flowNodeDefinition){
            this.flowNodeDefinition = flowNodeDefinition;
            return this;
        }

        public FlowNodeExecution processInstance(ProcessInstanceExecution processInstance){
            this.processInstance = processInstance;
            return this;
        }

        public FlowNodeExecution processDefinition(ProcessDefinition processDefinition){
            this.processDefinition = processDefinition;
            return this;
        }

        public FlowNodeExecution onError(Supplier<Exception> supplier){
            this.errorSupplier = supplier;
            return this;
        }

        public FlowNodeExecutionResult execute(){

            try{
                this.startedAt = Instant.now();
                this.continuation = executeAndGetContinuation(flowNodeDefinition, processInstance, processDefinition);
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
                    .processDefinitionSnapshot(processDefinition)
                    .processInstanceSnapshot(ProcessInstanceMapper.takeSnapshot(processInstance))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .nodeExecutionStatus(nodeExecutionStatus)
                    .build() : null;

            return new FlowNodeExecutionResult(flowNodeExecutionSnapshot, continuation);
        }
    }

    public UnitOfWorkResult runWhileNotFindAStopPoint(FlowNodeDefinition startPoint, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition){
        FlowNodeDefinition currentNode = startPoint;

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
                flowNodeExecuted.setProcessInstanceId(flowNodeExecutionSnapshot.processInstance().id());
                flowNodeExecuted.setProcessDefinitionId(flowNodeExecutionSnapshot.processDefinition().id());
                flowNodeExecuted.setNodeExecutionStatus(flowNodeExecutionSnapshot.nodeExecutionStatus());
                flowNodeExecuted.setStartedAt(flowNodeExecutionSnapshot.startedAt());
                flowNodeExecuted.setFinishedAt(flowNodeExecutionSnapshot.finishedAt());
                OutboxEventEntity outboxEvent = new OutboxEventEntity(flowNodeExecuted);
                criticalEvents.add(outboxEvent);
            }


            if (continuation == null || continuation.isAsynchronous()) {
                return new UnitOfWorkResult( new UnitOfWork(ProcessInstanceMapper.mapToEntity(processInstance),
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
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
                            null,
                            Collections.emptyList(),
                            Collections.emptyList(),
                            Collections.emptyList(),
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
        FlowNodeDefinition flowNodeDefinition = executionContext.getFlowNode();
        if(flowNodeDefinition instanceof Executable){
            taskExecutor.execute(executionContext);
        }
        //TODO
        //think in history!!!!
    }



    private Continuation executeAndGetContinuation(FlowNodeDefinition flowNodeDefinition, ProcessInstanceExecution processInstance, ProcessDefinition processDefinition){
        // O ExecutionContext agora é criado com a instância mutável, permitindo que os delegates alterem seu estado.
        ExecutionContext executionContext = new DefaultExecutionContext(processInstance, processDefinition, flowNodeDefinition);
        execute(executionContext);

        boolean isCommitAfter = isCommitAfter(flowNodeDefinition);
        return navigator.determineNextContinuation(flowNodeDefinition, processDefinition, isCommitAfter);
    }

    private boolean isCommitAfter(FlowNodeDefinition flowNodeDefinition) {
        return Boolean.TRUE.equals(flowNodeDefinition.commitAfter());
    }

    private boolean isWaitState(FlowNodeDefinition flowNodeDefinition){
        return flowNodeDefinition instanceof WaitState;
    }

    private boolean isCommitBefore(FlowNodeDefinition flowNodeDefinition){
        return Boolean.TRUE.equals(flowNodeDefinition.commitBefore());
    }
}
