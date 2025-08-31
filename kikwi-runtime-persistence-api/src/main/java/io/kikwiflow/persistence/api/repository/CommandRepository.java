package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.bpmn.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.util.Map;
import java.util.Optional;

public interface CommandRepository {

    /**
     * Cria uma nova instância de processo na base de dados.
     *
     * @param instance O objeto ProcessInstance a ser persistido.
     */
    ProcessInstance saveProcessInstance(ProcessInstance instance);

    /**
     * Atualiza as variáveis de uma instância de processo existente.
     *
     * @param processInstanceId O ID da instância a ser atualizada.
     * @param variables O mapa de variáveis a serem adicionadas ou sobrescritas.
     */
    void updateVariables(String processInstanceId, Map<String, Object> variables);

    /**
     * Adiciona uma nova tarefa à fila de execução.
     *
     * @param task A tarefa executável a ser criada.
     */
    ExecutableTask createExecutableTask(ExecutableTask task);

    ExternalTask createExternalTask(ExternalTask task);

    Optional<ExternalTask> completeExternalTask(String externalTaskId);

    ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy);

    void deleteProcessInstanceById(String processInstanceId);

    void commitWork(UnitOfWork unitOfWork);
}
