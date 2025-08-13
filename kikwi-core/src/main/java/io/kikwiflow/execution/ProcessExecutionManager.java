package io.kikwiflow.execution;

import io.kikwiflow.event.AsynchronousEventPublisher;
import io.kikwiflow.execution.dto.StartableProcessRecord;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.execution.dto.UnitOfWorkResult;
import io.kikwiflow.model.bpmn.ProcessDefinitionSnapshot;
import io.kikwiflow.model.bpmn.elements.FlowNodeDefinitionSnapshot;
import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

public class ProcessExecutionManager {
    private final FlowNodeExecutor flowNodeExecutor;
    private final KikwiEngineRepository  repository;
    private final AsynchronousEventPublisher asynchronousEventPublisher;

    public ProcessExecutionManager(FlowNodeExecutor flowNodeExecutor, KikwiEngineRepository repository, AsynchronousEventPublisher asynchronousEventPublisher) {
        this.flowNodeExecutor = flowNodeExecutor;
        this.repository = repository;
        this.asynchronousEventPublisher = asynchronousEventPublisher;
    }

    public UnitOfWorkResult startProcessExecution(StartableProcessRecord startableProcessRecord){
        FlowNodeDefinitionSnapshot startPoint = startableProcessRecord.processDefinition().defaultStartPoint();
        return flowNodeExecutor.runWhileNotFindAStopPoint(startPoint, startableProcessRecord.processInstance(), startableProcessRecord.processDefinition());
    }


    public Continuation execute(FlowNodeDefinitionSnapshot startPoint, ProcessInstance instance, ProcessDefinitionSnapshot definition) {
        // 1. DELEGA A EXECUÇÃO para o FlowNodeExecutor.
        UnitOfWorkResult result = flowNodeExecutor.runWhileNotFindAStopPoint(startPoint, instance, definition);

        UnitOfWork uow = result.unitOfWork();
        if (uow != null) {
            repository.commitWork(uow);

           /* if (!uow.lightweightEvents().isEmpty()) {
                asynchronousEventPublisher.publishEvents(uow.lightweightEvents());
            }*/
        }

        // 4. RETORNA A CONTINUAÇÃO para a engine principal.
        return result.continuation();
    }
}
