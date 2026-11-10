package io.kikwiflow.persistence.api.repository;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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

    /**
     * Localiza uma ExternalTask ativa (EVENT_CATCHER standalone ou filha de um grupo) pela sua chave de
     * correlação externa, restrita ao tenant informado.
     */
    Optional<ExternalTask> findExternalTaskByCorrelationKey(String correlationKey, String tenantId);

    /**
     * Apaga atomicamente a tarefa-filha {@code childTaskId} e resolve a pendência na tarefa-mãe
     * {@code parentTaskId} de acordo com a {@code matchPolicy} (ALL: remove a chave da lista de pendências e
     * verifica se zerou; ANY: tenta transicionar o status da mãe para COMPLETED via CAS).
     *
     * @return {@code true} se esta chamada foi responsável por satisfazer a matchPolicy — o chamador deve, em
     *         seguida, completar a tarefa-mãe normalmente (ex.: via {@code completeExternalTask}).
     */
    boolean resolveCorrelationChild(String childTaskId, String parentTaskId, MatchPolicy matchPolicy);

    /**
     * Protocolo de worker com lease (docs/engine/31, E2). Adquire até {@code maxTasks} tarefas com
     * {@code topicName} em {@code topics} que estejam adquiríveis em {@code now} (ver
     * {@code ExternalTask.isFetchable}), gravando {@code lockOwner = workerId} e {@code lockExpiresAt}. Cada
     * tarefa é travada atomicamente (uma de cada vez, como {@code findAndLockDueTasks}); dois workers nunca
     * recebem a mesma tarefa.
     *
     * @param tenantId     {@code null} = qualquer tenant
     * @param eventFactory recebe a tarefa já travada e devolve os eventos gravados junto com o lock
     * @return as tarefas travadas, no estado gravado
     */
    List<ExternalTask> fetchAndLockExternalTasks(String workerId, Collection<String> topics, String tenantId,
                                                 int maxTasks, Instant now, Instant lockExpiresAt,
                                                 Function<ExternalTask, List<OutboxEventEntity>> eventFactory);

    /**
     * Trava a tarefa {@code taskId} para {@code workerId}, se ela estiver adquirível em {@code now} (mesmo critério
     * de {@code ExternalTask.isFetchable}): o worker escolheu esta tarefa, ex.: a partir de uma lista. Atômico, como
     * {@link #fetchAndLockExternalTasks}: dois workers nunca travam a mesma tarefa.
     *
     * @param tenantId     {@code null} = qualquer tenant
     * @param eventFactory recebe a tarefa já travada e devolve os eventos gravados junto com o lock
     * @return a tarefa travada, ou vazio se ela não existe, é de outro tenant ou não está adquirível
     */
    Optional<ExternalTask> lockExternalTask(String taskId, String workerId, String tenantId, Instant now,
                                            Instant lockExpiresAt,
                                            Function<ExternalTask, List<OutboxEventEntity>> eventFactory);

    /**
     * Substitui a tarefa por {@code replacement} só se ela ainda estiver em posse de {@code workerId} em
     * {@code now} (lease vigente). Usado para renovar o lease, registrar falha (com o incidente quando os
     * retries acabam) e anexar atividades — tudo no mesmo commit dos eventos.
     *
     * @return {@code false} se a tarefa não existe mais ou o lease não é de {@code workerId}; nada é gravado
     */
    boolean updateLockedExternalTask(ExternalTask replacement, String workerId, Instant now,
                                     List<Incident> incidentsToCreate, List<OutboxEventEntity> events);

    /**
     * Grava a tarefa como está, sem condição de lease, resolvendo incidentes no mesmo commit. Usado por
     * {@code retryIncident} de uma tarefa externa que esgotou os retries.
     */
    void saveExternalTask(ExternalTask task, List<Incident> incidentsToUpdate, List<OutboxEventEntity> events);
}
