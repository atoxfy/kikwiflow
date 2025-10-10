package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CommandRepository {

    /**
     * Cria uma nova instância de processo na base de dados.
     *
     * @param instance O objeto ProcessInstance a ser persistido.
     */
    ProcessInstance saveProcessInstance(ProcessInstance instance);

    ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy);

    void commitWork(UnitOfWork unitOfWork);

    List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId);

    ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables);

    void claim(String externalTaskId, String assignee);

    void unclaim(String externalTaskId);

}
