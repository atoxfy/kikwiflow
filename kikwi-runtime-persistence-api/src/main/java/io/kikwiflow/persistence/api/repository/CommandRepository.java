package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CommandRepository {

    ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy);

    void commitWork(UnitOfWork unitOfWork);

    List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId, long lockTimeoutMillis);

    ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables);

    void claim(String externalTaskId, String assignee);

    void unclaim(String externalTaskId);

    void deleteProcessInstanceById(String processInstanceId);
}
