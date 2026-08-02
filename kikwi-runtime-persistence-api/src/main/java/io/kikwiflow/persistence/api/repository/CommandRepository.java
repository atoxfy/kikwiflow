package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CommandRepository {

    ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy);

    void commitWork(UnitOfWork unitOfWork);

    List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId, long lockTimeoutMillis);

    /**
     * @param events critical events já construídos pelo chamador (ex.: {@code PROCESS_VARIABLE_CHANGED}) para
     *               serem persistidos atomicamente junto com a mudança de variáveis. Pode ser {@code null}/vazio.
     */
    ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables, List<OutboxEventEntity> events);

    /**
     * @param events critical events já construídos pelo chamador (ex.: {@code PROCESS_VARIABLE_CHANGED} com
     *               {@code removed=true}) para serem persistidos atomicamente junto com a remoção. Pode ser
     *               {@code null}/vazio.
     */
    ProcessInstance unsetVariables(String processInstanceId, Set<String> variableNames, List<OutboxEventEntity> events);

    /**
     * @param events critical events já construídos pelo chamador (ex.: {@code EXTERNAL_TASK_CLAIMED}) para
     *               serem persistidos atomicamente junto com o claim. Pode ser {@code null}/vazio.
     */
    void claim(String externalTaskId, String assignee, List<OutboxEventEntity> events);

    /**
     * @param events critical events já construídos pelo chamador (ex.: {@code EXTERNAL_TASK_UNCLAIMED}) para
     *               serem persistidos atomicamente junto com o unclaim. Pode ser {@code null}/vazio.
     */
    void unclaim(String externalTaskId, List<OutboxEventEntity> events);

    void deleteProcessInstanceById(String processInstanceId);
}
