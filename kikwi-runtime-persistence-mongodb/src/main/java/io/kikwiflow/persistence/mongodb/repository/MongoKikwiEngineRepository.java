/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kikwiflow.persistence.mongodb.repository;

import com.mongodb.MongoCommandException;
import com.mongodb.ReadPreference;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.OrphanedChildCompletion;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskType;
import io.kikwiflow.model.execution.enumerated.ExternalTaskStatus;
import io.kikwiflow.model.execution.enumerated.MatchPolicy;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.AttachedTaskType;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.persistence.api.data. UnitOfWork;
import io.kikwiflow.persistence.api.data.VariableOpType;
import io.kikwiflow.persistence.api.data.VariableOperation;
import io.kikwiflow.persistence.api.exception.OptimisticLockingFailureException;
import io.kikwiflow.persistence.api.query.ExternalTaskQuery;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.persistence.mongodb.mapper.ExecutableTaskMapper;
import io.kikwiflow.persistence.mongodb.mapper.ExternalTaskMapper;
import io.kikwiflow.persistence.mongodb.mapper.IncidentMapper;
import io.kikwiflow.persistence.mongodb.mapper.InstantMapper;
import io.kikwiflow.persistence.mongodb.mapper.definition.ProcessDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessInstanceMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessVariableMapper;
import io.kikwiflow.persistence.mongodb.mapper.event.OutboxEventMapper;
import io.kikwiflow.persistence.mongodb.util.MongoKeyEncoder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;
import static com.mongodb.client.model.Filters.or;

public class MongoKikwiEngineRepository implements KikwiEngineRepository {
    
    private final String PROCESS_DEFINITION_COLLECTION = "process_definitions";
    private final String PROCESS_INSTANCE_COLLECTION = "process_instances";
    private final String EXTERNAL_TASK_COLLECTION = "external_tasks";
    private final String EXECUTABLE_TASK_COLLECTION = "executable_tasks";
    private final String INCIDENTS_COLLECTION = "incidents";
    private final String OUTBOX_EVENTS_COLLECTION = "outbox_events";

    // Documentado como máximo em docs/apis/process-instances/search/api-guide.md — antes desta constante, nada
    // no código impedia um `size` maior que este.
    private static final int MAX_PAGE_SIZE = 100;

    private final MongoClient mongoClient;
    private final String databaseName;
    private final boolean outboxPersistenceEnabled;
    private final long outboxTtlSeconds;

    public MongoKikwiEngineRepository(MongoClient mongoClient, String databaseName, boolean outboxPersistenceEnabled) {
        this(mongoClient, databaseName, outboxPersistenceEnabled, 0L);
    }

    public MongoKikwiEngineRepository(MongoClient mongoClient, String databaseName, boolean outboxPersistenceEnabled, long outboxTtlSeconds) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.outboxPersistenceEnabled = outboxPersistenceEnabled;
        this.outboxTtlSeconds = outboxTtlSeconds;
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName);
    }

    @Override
    public KKFMetrics getProcessMacroMetrics(String processDefinitionId) {
        long running = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION)
                .withReadPreference(ReadPreference.secondaryPreferred())
                .countDocuments(and(
                        eq("processDefinitionId", processDefinitionId),
                        eq("status", "ACTIVE")
                ));

        long failed = getDatabase().getCollection(INCIDENTS_COLLECTION)
                .withReadPreference(ReadPreference.secondaryPreferred())
                .countDocuments(and(
                        eq("processDefinitionId", processDefinitionId),
                        eq("status", "OPEN")
                ));

        return new KKFMetrics(running, 100.0, failed);
    }

    public long countExecutableTasksByDefinitionId(String id){
        return getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION).countDocuments(
                eq("taskDefinitionId", id)
        );
    }

    public long countExternalTasksByDefinitionId(String id){
        // Exclui CORRELATED: uma filha de EVENT_CATCHER GROUP já correlacionada, mas ainda não limpa (aguarda
        // a mãe concluir) não deveria contar como "ativa" numa métrica genérica de contagem.
        return getDatabase().getCollection(EXTERNAL_TASK_COLLECTION).countDocuments(
                and(eq("taskDefinitionId", id), ne("status", ExternalTaskStatus.CORRELATED.name()))
        );
    }

    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionToSave) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);
        Document doc = ProcessDefinitionMapper.toDocument(processDefinitionToSave);
        collection.replaceOne(eq("_id", processDefinitionToSave.id()), doc, new ReplaceOptions().upsert(true));
        return processDefinitionToSave;
    }


    @Override
    public Optional<ProcessDefinition> findProcessDefinitionByKey(String processDefinitionKey) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);

        Document doc = collection.find(eq("key", processDefinitionKey))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        return Optional.ofNullable(doc)
                .map(ProcessDefinitionMapper::fromDocument);
    }


    @Override
    public Optional<ProcessDefinition> findProcessDefinitionById(String processDefinitionId) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);

        Document doc = collection.find(eq("_id", processDefinitionId)).first();

        return Optional.ofNullable(doc)
                .map(ProcessDefinitionMapper::fromDocument);
    }

    public long countOpenIncidentsByProcessDefinition(String processDefinitionId) {
        return getDatabase().getCollection(INCIDENTS_COLLECTION).countDocuments(
                and(
                        eq("processDefinitionId", processDefinitionId),
                        eq("status", "OPEN")
                )
        );
    }

    @Override
    public long countProcessInstancesByProcessDefinition(String processDefinitionId) {
        return getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION).countDocuments(
                eq("processDefinitionId", processDefinitionId)
        );
    }

    @Override
    public List<Incident> findIncidentsByProcessInstanceId(String processInstanceId) {
        MongoCollection<Document> collection = getDatabase().getCollection(INCIDENTS_COLLECTION);
        List<Incident> list = new ArrayList<>();

        collection.find(eq("processInstanceId", processInstanceId))
                .forEach(doc -> list.add(IncidentMapper.fromDocument(doc))); // Certifique-se de ter o fromDocument no seu mapper
        return list;
    }

    @Override
    public Optional<Incident> findIncidentById(String incidentId) {
        MongoCollection<Document> collection = getDatabase().getCollection(INCIDENTS_COLLECTION);
        Document doc = collection.find(eq("_id", incidentId)).first();
        return Optional.ofNullable(doc).map(IncidentMapper::fromDocument);
    }


    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        try (ClientSession clientSession = mongoClient.startSession()) {
            clientSession.withTransaction(() -> {
                MongoCollection<Document> processInstances = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
                MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
                MongoCollection<Document> executableTasks = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);
                MongoCollection<Document> incidents = getDatabase().getCollection(INCIDENTS_COLLECTION);

                // Guard de finalização (ver Javadoc de UnitOfWork.finalizingNodeId) — roda antes de qualquer
                // outra escrita desta transação. Cobre tanto "pai concluiu normalmente enquanto um boundary
                // event disparava" quanto "dois boundary events interruptivos (timer + catch event) dispararam
                // ao mesmo tempo": um `deleteOne` por _id é atômico por natureza no Mongo, então só quem
                // primeiro apagar esta linha específica vence e prossegue — a outra transação, ao tentar a
                // mesma operação, apaga zero documentos, e abortamos ela inteira sem escrever nada. A cascata
                // dos irmãos (attachedToRefId) só roda no caminho vencedor, depois do guard.
                // cancelledChildEvents é preenchido abaixo, só quando o nó finalizado é um EXECUTABLE_TASK —
                // única forma de ser um CALL_ACTIVITY_COORDINATOR (ver justificativa de performance no Javadoc
                // de cancelActiveChildSubtrees). Declarado aqui porque precisa estar em escopo para o merge em
                // allEvents mais abaixo, mas populado dentro do guard, não do bloco de executableTasksToDelete
                // (que roda em praticamente toda conclusão de tarefa do motor — gatilho errado para isto).
                List<OutboxEventEntity> cancelledChildEvents = new ArrayList<>();

                if (unitOfWork.finalizingNodeId() != null) {
                    MongoCollection<Document> guardedCollection = unitOfWork.finalizingNodeType() == AttachedTaskType.EXECUTABLE_TASK
                            ? executableTasks : externalTasks;

                    DeleteResult guardResult = guardedCollection.deleteOne(clientSession, eq("_id", unitOfWork.finalizingNodeId()));

                    if (guardResult.getDeletedCount() == 0) {
                        throw new OptimisticLockingFailureException(
                                "O nó " + unitOfWork.finalizingNodeId() + " já foi finalizado por um evento concorrente (boundary event ou conclusão normal).");
                    }

                    executableTasks.deleteMany(clientSession, eq("attachedToRefId", unitOfWork.finalizingNodeId()));
                    externalTasks.deleteMany(clientSession, eq("attachedToRefId", unitOfWork.finalizingNodeId()));

                    // Cancelamento recursivo de instâncias filhas já iniciadas — só pode ser relevante quando o
                    // nó finalizado é um EXECUTABLE_TASK (todo CALL_ACTIVITY_COORDINATOR é um; um EXTERNAL_TASK
                    // nunca é). Ver docs/engine/20-subprocessos-call-activity-especificacao.md, §5.
                    if (unitOfWork.finalizingNodeType() == AttachedTaskType.EXECUTABLE_TASK) {
                        cancelledChildEvents.addAll(cancelActiveChildSubtrees(
                                clientSession, processInstances, executableTasks, externalTasks,
                                unitOfWork.finalizingNodeId()));
                    }
                }

                if (unitOfWork.instanceToCreate() != null) {
                    Document instanceDoc = ProcessInstanceMapper.toDocument(unitOfWork.instanceToCreate());
                    Document initialActiveNodes = new Document();

                    if (unitOfWork.executableTasksToCreate() != null) {
                        for (ExecutableTask t : unitOfWork.executableTasksToCreate()) {
                            String definitionId = t.taskDefinitionId();
                            initialActiveNodes.put(definitionId, initialActiveNodes.getInteger(definitionId, 0) + 1);
                        }
                    }

                    if (unitOfWork.externalTasksToCreate() != null) {
                        for (ExternalTask t : unitOfWork.externalTasksToCreate()) {
                            String definitionId = t.taskDefinitionId();
                            initialActiveNodes.put(definitionId, initialActiveNodes.getInteger(definitionId, 0) + 1);
                        }
                    }

                    if (!initialActiveNodes.isEmpty()) {
                        instanceDoc.put("activeNodes", initialActiveNodes);
                    }

                    processInstances.insertOne(clientSession, instanceDoc);
                }

                if (unitOfWork.instanceToDelete() != null) {
                    String instanceId = unitOfWork.instanceToDelete().id();
                    externalTasks.deleteMany(clientSession, eq("processInstanceId", instanceId));
                    executableTasks.deleteMany(clientSession, eq("processInstanceId", instanceId));
                    incidents.deleteMany(clientSession, eq("processInstanceId", instanceId));
                    processInstances.deleteOne(clientSession, eq("_id", instanceId));
                }

                if (unitOfWork.instanceToUpdate() != null) {
                    ProcessInstance instance = unitOfWork.instanceToUpdate();

                    List<Bson> updates = new ArrayList<>();
                    updates.add(Updates.inc("version", 1));

                    Map<String, Integer> nodeDeltas = new java.util.HashMap<>();

                    if (unitOfWork.finishedNodeDefinitions() != null) {
                        for (String nodeId : unitOfWork.finishedNodeDefinitions()) {
                            nodeDeltas.put(nodeId, nodeDeltas.getOrDefault(nodeId, 0) - 1);
                        }
                    }

                    if (unitOfWork.executableTasksToCreate() != null) {
                        for (ExecutableTask t : unitOfWork.executableTasksToCreate()) {
                            nodeDeltas.put(t.taskDefinitionId(), nodeDeltas.getOrDefault(t.taskDefinitionId(), 0) + 1);
                        }
                    }

                    if (unitOfWork.externalTasksToCreate() != null) {
                        for (ExternalTask t : unitOfWork.externalTasksToCreate()) {
                            nodeDeltas.put(t.taskDefinitionId(), nodeDeltas.getOrDefault(t.taskDefinitionId(), 0) + 1);
                        }
                    }

                    nodeDeltas.forEach((nodeId, delta) -> {
                        if (delta != 0) {
                            updates.add(Updates.inc("activeNodes." + nodeId, delta));
                        }
                    });

                    if (instance.status() != null) {
                        updates.add(Updates.set("status", instance.status().name()));
                    }
                    if (instance.endedAt() != null) {
                        updates.add(Updates.set("endedAt", instance.endedAt()));
                    }
                    if (instance.businessValue() != null) {
                        updates.add(Updates.set("businessValue", new org.bson.types.Decimal128(instance.businessValue())));
                    }

                    if (instance.variables() != null) {
                        Map<String, VariableOperation> variableOps = unitOfWork.variableOperations();

                        if (variableOps != null && !variableOps.isEmpty()) {
                            variableOps.forEach((key, operation) -> {
                                String fieldPath = "variables." + MongoKeyEncoder.encode(key);

                                if (operation.type() == VariableOpType.SET) {
                                    updates.add(Updates.set(fieldPath, ProcessVariableMapper.toDocument(operation.value())));
                                }
                                else if (operation.type() == VariableOpType.UNSET) {
                                    updates.add(Updates.unset(fieldPath));
                                }
                            });
                        }
                    }

                    Bson filter = eq("_id", instance.id());
                    UpdateResult result = processInstances.updateOne(clientSession, filter, Updates.combine(updates));

                    if (result.getMatchedCount() == 0) {
                        throw new OptimisticLockingFailureException("The instance " + instance.id() + " was not found for update.");
                    }
                }


                if (unitOfWork.incidentsToCreate() != null && !unitOfWork.incidentsToCreate().isEmpty()) {
                    List<InsertOneModel<Document>> writes = new ArrayList<>();
                    unitOfWork.incidentsToCreate().forEach(inc ->
                            writes.add(new InsertOneModel<>(IncidentMapper.toDocument(inc)))
                    );
                    incidents.bulkWrite(clientSession, writes);
                }

                if (unitOfWork.incidentsToUpdate() != null && !unitOfWork.incidentsToUpdate().isEmpty()) {
                    List<WriteModel<Document>> incidentUpdates = new ArrayList<>();
                    unitOfWork.incidentsToUpdate().forEach(inc -> {
                        Document incDoc = IncidentMapper.toDocument(inc);
                        incidentUpdates.add(new ReplaceOneModel<>(eq("_id", inc.id()), incDoc));
                    });
                    incidents.bulkWrite(clientSession, incidentUpdates);
                }


                List<WriteModel<Document>> externalTaskWrites = new ArrayList<>();
                if (unitOfWork.externalTasksToCreate() != null && !unitOfWork.externalTasksToCreate().isEmpty()) {
                    unitOfWork.externalTasksToCreate().forEach(task ->
                            externalTaskWrites.add(new InsertOneModel<>(ExternalTaskMapper.toDocument(task)))
                    );
                }
                
                if (unitOfWork.externalTasksToDelete() != null && !unitOfWork.externalTasksToDelete().isEmpty()) {
                    externalTaskWrites.add(new DeleteManyModel<>(in("_id", unitOfWork.externalTasksToDelete())));
                }
                
                if (!externalTaskWrites.isEmpty()) {
                    externalTasks.bulkWrite(clientSession, externalTaskWrites);
                }

                if (unitOfWork.externalTasksToDelete() != null && !unitOfWork.externalTasksToDelete().isEmpty()) {
                    // Cascata genérica: qualquer ExternalTask filha (EVENT_CATCHER GROUP) cujo coordinatorTaskId
                    // aponte para uma tarefa apagada nesta mesma transação também é removida — cobre timeout de
                    // boundary timer na mãe e limpeza de irmãs remanescentes na política ANY, sem lógica
                    // específica de EVENT_CATCHER em ContinuationService.
                    externalTasks.deleteMany(clientSession, in("coordinatorTaskId", unitOfWork.externalTasksToDelete()));
                }

                List<WriteModel<Document>> executableTaskWrites = new ArrayList<>();
                if (unitOfWork.executableTasksToCreate() != null && !unitOfWork.executableTasksToCreate().isEmpty()) {
                    unitOfWork.executableTasksToCreate().forEach(task ->
                            executableTaskWrites.add(new InsertOneModel<>(ExecutableTaskMapper.toDocument(task)))
                    );
                }
                if (unitOfWork.executableTasksToDelete() != null && !unitOfWork.executableTasksToDelete().isEmpty()) {
                    executableTaskWrites.add(new DeleteManyModel<>(in("_id", unitOfWork.executableTasksToDelete())));
                    // Cascata análoga à de ExternalTask/coordinatorTaskId acima, mas para CALL_ACTIVITY_STARTER:
                    // quando a coordenadora é apagada (timeout do boundary event na coordenadora — ver
                    // docs/engine/20-subprocessos-call-activity-especificacao.md, §5), qualquer iniciadora
                    // ainda pendente (que aponta para a coordenadora via joinTaskId) também é removida. Escopo
                    // restrito ao tipo CALL_ACTIVITY_STARTER (não um cascade genérico por joinTaskId) para não
                    // arriscar tocar ramificações de PARALLEL_GATEWAY/JOIN_GATEWAY ainda em andamento.
                    executableTaskWrites.add(new DeleteManyModel<>(and(
                            in("joinTaskId", unitOfWork.executableTasksToDelete()),
                            eq("type", ExecutableTaskType.CALL_ACTIVITY_STARTER.name()))));
                }

                if (unitOfWork.executableTasksToUpdate() != null && !unitOfWork.executableTasksToUpdate().isEmpty()) {
                    unitOfWork.executableTasksToUpdate().forEach(task -> {
                        Document taskDoc = ExecutableTaskMapper.toDocument(task);
                        executableTaskWrites.add(new ReplaceOneModel<>(eq("_id", task.id()), taskDoc));
                    });
                }

                if (!executableTaskWrites.isEmpty()) {
                    executableTasks.bulkWrite(clientSession, executableTaskWrites);
                }

                List<OutboxEventEntity> orphanEvents = new ArrayList<>();
                if (unitOfWork.branchPullIntentions() != null && !unitOfWork.branchPullIntentions().isEmpty()) {
                    for (BranchPullIntention intention : unitOfWork.branchPullIntentions()) {
                        org.bson.conversions.Bson filter = com.mongodb.client.model.Filters.eq("_id", intention.joinTaskId());

                        List<org.bson.conversions.Bson> updatePipeline = List.of(
                                new Document("$set", new Document("pendingBranchIds",
                                        new Document("$filter", new Document("input", "$pendingBranchIds")
                                                .append("as", "b")
                                                .append("cond", new Document("$ne", List.of("$$b", intention.branchId())))))),

                                new Document("$set", new Document("status",
                                        new Document("$cond", new Document("if", new Document("$eq", List.of(new Document("$size", "$pendingBranchIds"), 0)))
                                                .append("then", "PENDING")
                                                .append("else", "$status"))))
                        );

                        UpdateResult result = executableTasks.updateOne(clientSession, filter, updatePipeline);

                        // Ver docs/engine/20-subprocessos-call-activity-especificacao.md, §4.4: se a
                        // coordenadora já não existe mais (timeout do boundary event apagou-a antes), o
                        // updateOne casa zero documentos sem erro — em vez de deixar passar silencioso,
                        // registra ORPHANED_CHILD_COMPLETION. A identidade da instância filha vem do próprio
                        // unitOfWork (é o commit da sua própria conclusão).
                        if (result.getMatchedCount() == 0) {
                            ProcessInstance childInstance = unitOfWork.instanceToDelete() != null
                                    ? unitOfWork.instanceToDelete()
                                    : unitOfWork.instanceToUpdate();

                            orphanEvents.add(new OutboxEventEntity(CriticalEventType.ORPHANED_CHILD_COMPLETION,
                                    new OrphanedChildCompletion(
                                            childInstance != null ? childInstance.id() : null,
                                            childInstance != null ? childInstance.processDefinitionId() : null,
                                            childInstance != null ? childInstance.tenantId() : null,
                                            childInstance != null ? childInstance.parentInstanceId() : null,
                                            intention.joinTaskId(),
                                            intention.branchId(),
                                            Instant.now())));
                        }
                    }
                }

                List<OutboxEventEntity> allEvents = new ArrayList<>();
                if (unitOfWork.events() != null) {
                    allEvents.addAll(unitOfWork.events());
                }
                allEvents.addAll(orphanEvents);
                allEvents.addAll(cancelledChildEvents);

                if (outboxPersistenceEnabled && !allEvents.isEmpty()) {
                    MongoCollection<Document> outboxEvents = getDatabase().getCollection(OUTBOX_EVENTS_COLLECTION);
                    List<InsertOneModel<Document>> eventWrites = allEvents.stream()
                            .map(OutboxEventMapper::toDocument)
                            .map(InsertOneModel::new)
                            .toList();
                    outboxEvents.bulkWrite(clientSession, eventWrites);
                }

                return "Transaction committed";
            });
        }
    }

    /**
     * Cancelamento recursivo de subárvore(s) de {@code ProcessInstance} spawnada(s) por uma coordenadora
     * {@code CALL_ACTIVITY_COORDINATOR} apagada nesta transação via o guard de finalização (ver
     * docs/engine/20-subprocessos-call-activity-especificacao.md, §5). BFS por nível, todo dentro do mesmo
     * {@code clientSession}/transação do commit que apagou a coordenadora:
     * <p>
     * Nível 0: toda instância ACTIVE cujo {@code callerTaskId} é {@code coordinatorTaskId} — os filhos diretos
     * desta coordenadora especificamente ({@code callerTaskId} escopa por coordenadora, nunca confunde call
     * activities irmãs no mesmo processo). Níveis seguintes: toda instância ACTIVE cujo {@code parentInstanceId}
     * está no nível anterior — cobre netos/bisnetos de call activities aninhadas dentro do filho, independente
     * de qual coordenadora interna os gerou.
     * <p>
     * Cada instância do fechamento tem suas tasks apagadas (mesmo escopo de {@code instanceToDelete} acima), a
     * própria linha apagada (nunca mantida com {@code status=CANCELLED} — a coleção de runtime é estado
     * operacional, não histórico, mesma decisão já aplicada a {@code instanceToDelete}), e um evento
     * {@code PROCESS_INSTANCE_FINISHED} com {@code status=CANCELLED} é acumulado no retorno para entrar no
     * mesmo commit — nunca a exclusão sem o evento correspondente. Incidentes não são tocados (histórico).
     * <p>
     * <b>Nota de performance</b>: o chamador só invoca este método dentro do guard de
     * {@code UnitOfWork.finalizingNodeId} (boundary event interruptivo finalizando um nó) — nunca a partir de
     * {@code executableTasksToDelete} genérico, que é populado em praticamente toda conclusão de
     * {@code ExecutableTask} do motor (qualquer tipo, não só coordenadora). Rodar esta consulta ali faria um
     * `find` em {@code process_instances} por {@code callerTaskId} em cada conclusão de tarefa do sistema
     * inteiro — a esmagadora maioria sem nenhum call activity envolvido. `finalizingNodeId` é populado só para
     * finalizações por boundary event, ordens de magnitude mais raro. A consulta do nível 0 usa o índice
     * {@code caller_task_idx} (ver {@code ensureIndexes}); sem ele seria um COLLSCAN a cada chamada.
     */
    private List<OutboxEventEntity> cancelActiveChildSubtrees(ClientSession clientSession,
                                                               MongoCollection<Document> processInstances,
                                                               MongoCollection<Document> executableTasks,
                                                               MongoCollection<Document> externalTasks,
                                                               String coordinatorTaskId) {
        List<OutboxEventEntity> cancelledEvents = new ArrayList<>();

        List<Document> currentLevel = processInstances.find(clientSession, and(
                        eq("status", ProcessInstanceStatus.ACTIVE.name()),
                        eq("callerTaskId", coordinatorTaskId)))
                .into(new ArrayList<>());

        while (!currentLevel.isEmpty()) {
            List<String> currentIds = currentLevel.stream().map(doc -> doc.getString("_id")).toList();

            executableTasks.deleteMany(clientSession, in("processInstanceId", currentIds));
            externalTasks.deleteMany(clientSession, in("processInstanceId", currentIds));
            processInstances.deleteMany(clientSession, in("_id", currentIds));

            currentLevel.forEach(doc ->
                    cancelledEvents.add(buildCancelledEvent(ProcessInstanceMapper.fromDocument(doc))));

            currentLevel = processInstances.find(clientSession, and(
                            eq("status", ProcessInstanceStatus.ACTIVE.name()),
                            in("parentInstanceId", currentIds)))
                    .into(new ArrayList<>());
        }

        return cancelledEvents;
    }

    private OutboxEventEntity buildCancelledEvent(ProcessInstance instance) {
        ProcessInstanceFinished event = ProcessInstanceFinished.builder()
                .id(instance.id())
                .businessKey(instance.businessKey())
                .status(ProcessInstanceStatus.CANCELLED)
                .processDefinitionId(instance.processDefinitionId())
                .variables(instance.variables())
                .startedAt(instance.startedAt())
                .endedAt(Instant.now())
                .businessValue(instance.businessValue())
                .tenantId(instance.tenantId())
                .origin(instance.origin())
                .parentInstanceId(instance.parentInstanceId())
                .callerTaskId(instance.callerTaskId())
                .callerBranchId(instance.callerBranchId())
                .build();

        return new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_FINISHED, event);
    }

    @Override
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId, long lockTimeoutMillis) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);
        List<ExecutableTask> lockedTasks = new ArrayList<>();

        Instant lockExpirationThreshold = now.minusMillis(lockTimeoutMillis);

        java.util.Date nowDate = java.util.Date.from(now);
        java.util.Date thresholdDate = java.util.Date.from(lockExpirationThreshold);
        java.util.Date acquiredAtDate = java.util.Date.from(Instant.now());

        for (int i = 0; i < limit; i++) {
            Bson pendingFilter = and(
                    eq("status", ExecutableTaskStatus.PENDING.name()),
                    or(
                            eq("dueDate", null),
                            lte("dueDate", nowDate)
                    )
            );

            Bson stuckLockedFilter = and(
                    eq("status", ExecutableTaskStatus.LOCKED.name()),
                    lte("acquiredAt", thresholdDate)
            );

            Bson finalFilter = or(pendingFilter, stuckLockedFilter);

            Bson update = Updates.combine(
                    Updates.set("status", ExecutableTaskStatus.LOCKED.name()),
                    Updates.set("executorId", workerId),
                    Updates.set("acquiredAt", acquiredAtDate)
            );

            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                    .returnDocument(ReturnDocument.AFTER)
                    .sort(Sorts.ascending("dueDate"));

            Document lockedDoc = collection.findOneAndUpdate(finalFilter, update, options);

            if (lockedDoc == null) {
                break;
            }

            lockedTasks.add(ExecutableTaskMapper.fromDocument(lockedDoc));
        }

        return lockedTasks;
    }

    @Override
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables, List<OutboxEventEntity> events) {
        if (variables == null || variables.isEmpty()) {
            return findProcessInstanceById(processInstanceId).orElse(null);
        }

        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);

        List<Bson> updates = new ArrayList<>();
        for (Map.Entry<String, ProcessVariable> entry : variables.entrySet()) {
            String fieldPath = "variables." + MongoKeyEncoder.encode(entry.getKey());
            updates.add(Updates.set(fieldPath, ProcessVariableMapper.toDocument(entry.getValue())));
        }

        if (outboxPersistenceEnabled && events != null && !events.isEmpty()) {
            try (ClientSession clientSession = mongoClient.startSession()) {
                Document[] updatedDocHolder = new Document[1];
                clientSession.withTransaction(() -> {
                    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                    updatedDocHolder[0] = collection.findOneAndUpdate(clientSession, eq("_id", processInstanceId), Updates.combine(updates), options);
                    writeOutboxEvents(clientSession, events);
                    return "Transaction committed";
                });
                return ProcessInstanceMapper.fromDocument(updatedDocHolder[0]);
            }
        }

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document updatedDoc = collection.findOneAndUpdate(eq("_id", processInstanceId), Updates.combine(updates), options);

        return ProcessInstanceMapper.fromDocument(updatedDoc);
    }

    @Override
    public ProcessInstance unsetVariables(String processInstanceId, Set<String> variableNames, List<OutboxEventEntity> events) {
        if (variableNames == null || variableNames.isEmpty()) {
            return findProcessInstanceById(processInstanceId).orElse(null);
        }

        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);

        List<Bson> updates = new ArrayList<>();
        for (String variableName : variableNames) {
            updates.add(Updates.unset("variables." + MongoKeyEncoder.encode(variableName)));
        }

        if (outboxPersistenceEnabled && events != null && !events.isEmpty()) {
            try (ClientSession clientSession = mongoClient.startSession()) {
                Document[] updatedDocHolder = new Document[1];
                clientSession.withTransaction(() -> {
                    FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                    updatedDocHolder[0] = collection.findOneAndUpdate(clientSession, eq("_id", processInstanceId), Updates.combine(updates), options);
                    writeOutboxEvents(clientSession, events);
                    return "Transaction committed";
                });
                return ProcessInstanceMapper.fromDocument(updatedDocHolder[0]);
            }
        }

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document updatedDoc = collection.findOneAndUpdate(eq("_id", processInstanceId), Updates.combine(updates), options);

        return ProcessInstanceMapper.fromDocument(updatedDoc);
    }

    @Override
    public void claim(String externalTaskId, String assignee, List<OutboxEventEntity> events) {
        MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        if (outboxPersistenceEnabled && events != null && !events.isEmpty()) {
            try (ClientSession clientSession = mongoClient.startSession()) {
                clientSession.withTransaction(() -> {
                    externalTasks.updateOne(clientSession, eq("_id", externalTaskId), Updates.set("assignee", assignee));
                    writeOutboxEvents(clientSession, events);
                    return "Transaction committed";
                });
            }
            return;
        }

        externalTasks.updateOne(eq("_id", externalTaskId), Updates.set("assignee", assignee));
    }

    @Override
    public void unclaim(String externalTaskId, List<OutboxEventEntity> events) {
        MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        if (outboxPersistenceEnabled && events != null && !events.isEmpty()) {
            try (ClientSession clientSession = mongoClient.startSession()) {
                clientSession.withTransaction(() -> {
                    externalTasks.updateOne(clientSession, eq("_id", externalTaskId), Updates.unset("assignee"));
                    writeOutboxEvents(clientSession, events);
                    return "Transaction committed";
                });
            }
            return;
        }

        externalTasks.updateOne(eq("_id", externalTaskId), Updates.unset("assignee"));
    }

    /**
     * Espelha o trecho de {@code commitWork} que grava eventos no outbox — usado pelos comandos que não
     * passam por {@link UnitOfWork} ({@code claim}/{@code unclaim}/{@code addVariables}), para que o evento
     * seja gravado na mesma transação Mongo da mudança de estado.
     */
    private void writeOutboxEvents(ClientSession clientSession, List<OutboxEventEntity> events) {
        MongoCollection<Document> outboxEvents = getDatabase().getCollection(OUTBOX_EVENTS_COLLECTION);
        List<InsertOneModel<Document>> eventWrites = events.stream()
                .map(OutboxEventMapper::toDocument)
                .map(InsertOneModel::new)
                .toList();
        outboxEvents.bulkWrite(clientSession, eventWrites);
    }

    @Override
    public void deleteProcessInstanceById(String processInstanceId) {
        try (ClientSession clientSession = mongoClient.startSession()) {
            clientSession.withTransaction(() -> {
                getDatabase().getCollection(EXTERNAL_TASK_COLLECTION)
                        .deleteMany(clientSession, eq("processInstanceId", processInstanceId));

                getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION)
                        .deleteMany(clientSession, eq("processInstanceId", processInstanceId));

                getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION)
                        .deleteOne(clientSession, eq("_id", processInstanceId));

                // Decisão consciente: eventos em OUTBOX_EVENTS_COLLECTION NÃO são deletados aqui.
                // Essa coleção dobra como histórico durável de execução (ver findEventHistoryByProcessInstanceId),
                // então apagar uma instância concluída não deve destruir o rastro de por onde ela passou.
                return "Deletion transaction committed for instance " + processInstanceId;
            });
        }
    }

    @Override
    public List<ProcessDefinition> findAProcessDefinitionsByParams(String key) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);
        List<ProcessDefinition> definitions = new ArrayList<>();

        if (key == null || key.isBlank()) {
            return definitions;
        }

        collection.find(eq("key", key))
                .sort(Sorts.descending("version"))
                .map(ProcessDefinitionMapper::fromDocument)
                .into(definitions);

        return definitions;
    }

    @Override
    public List<ProcessDefinition> findAllProcessDefinitions() {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);
        List<ProcessDefinition> definitions = new ArrayList<>();
        collection.find()
                .sort(Sorts.descending("version"))
                .map(ProcessDefinitionMapper::fromDocument)
                .into(definitions);

        return definitions;
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
        
        Document doc = collection.find(eq("_id", processInstanceId)).first();

        return Optional.ofNullable(doc)
                .map(ProcessInstanceMapper::fromDocument);
    }

    @Override
    public List<ProcessInstance> findProcessInstancesByIdIn(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
        List<ProcessInstance> instances = new ArrayList<>();

        collection.find(in("_id", ids))
                .map(ProcessInstanceMapper::fromDocument)
                .into(instances);

        return instances;
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessInstanceId(String processInstanceId) {

        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
        List<ExternalTask> tasks = new ArrayList<>();

        collection.find(eq("processInstanceId", processInstanceId))
                .map(ExternalTaskMapper::fromDocument)
                .into(tasks);

        return tasks;
    }

    @Override
    public Optional<ExternalTask> findExternalTaskById(String externalTaskId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        Document doc = collection.find(eq("_id", externalTaskId)).first();

        return Optional.ofNullable(doc)
                .map(ExternalTaskMapper::fromDocument);
    }

    @Override
    public Optional<ExecutableTask> findExecutableTaskById(String executableTaskId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);

        Document doc = collection.find(eq("_id", executableTaskId)).first();

        return Optional.ofNullable(doc)
                .map(ExecutableTaskMapper::fromDocument);
    }

    @Override
    public Optional<ExecutableTask> findAndGetFirstPendingExecutableTask(String id) {
        //TODO usado somente para testes, implementação futura;
        return Optional.empty();
    }

    @Override
    public Optional<ExternalTask> findExternalTaskByCorrelationKey(String correlationKey, String tenantId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        // status=CREATED exclui tarefas já correlacionadas (CORRELATED, aguardando só a limpeza em cascata
        // quando a mãe concluir) — uma segunda correlação para a mesma chave não encontra nada, preservando a
        // proteção de idempotência mesmo sem apagar a linha imediatamente.
        Document doc = collection.find(and(
                eq("correlationKey", correlationKey),
                eq("tenantId", tenantId),
                eq("status", ExternalTaskStatus.CREATED.name())
        )).first();

        return Optional.ofNullable(doc).map(ExternalTaskMapper::fromDocument);
    }

    @Override
    public boolean resolveCorrelationChild(String childTaskId, String parentTaskId, MatchPolicy matchPolicy) {
        MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
        boolean[] satisfied = {false};

        try (ClientSession clientSession = mongoClient.startSession()) {
            clientSession.withTransaction(() -> {
                Document childDoc = externalTasks.find(clientSession, eq("_id", childTaskId)).first();
                String correlationKey = childDoc != null ? childDoc.getString("correlationKey") : null;

                // Não apaga a filha aqui — só marca CORRELATED. A limpeza real acontece via cascata por
                // coordinatorTaskId em commitWork, junto com a tarefa-mãe, preservando displayName/correlationKey
                // para o Monitor até lá (ver ExternalTaskStatus.CORRELATED).
                externalTasks.updateOne(clientSession, eq("_id", childTaskId),
                        Updates.set("status", ExternalTaskStatus.CORRELATED.name()));

                if (matchPolicy == MatchPolicy.ANY) {
                    // CAS: só quem primeiro flipar CREATED->COMPLETED "ganha" a corrida por essa tarefa-mãe.
                    Document winner = externalTasks.findOneAndUpdate(clientSession,
                            and(eq("_id", parentTaskId), eq("status", ExternalTaskStatus.CREATED.name())),
                            Updates.set("status", ExternalTaskStatus.COMPLETED.name()),
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                    satisfied[0] = winner != null;
                    return "resolveCorrelationChild(ANY)";
                }

                // ALL: mesmo padrão de $filter + $cond já usado para pendingBranchIds via BranchPullIntention
                // (ver bloco unitOfWork.branchPullIntentions() acima), agora sobre pendingCorrelationKeys.
                List<Bson> pipeline = List.of(
                        new Document("$set", new Document("pendingCorrelationKeys",
                                new Document("$filter", new Document("input", "$pendingCorrelationKeys")
                                        .append("as", "k")
                                        .append("cond", new Document("$ne", List.of("$$k", correlationKey)))))));

                Document after = externalTasks.findOneAndUpdate(clientSession,
                        eq("_id", parentTaskId), pipeline,
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

                satisfied[0] = after != null
                        && after.getList("pendingCorrelationKeys", String.class, Collections.emptyList()).isEmpty();
                return "resolveCorrelationChild(ALL)";
            });
        }

        return satisfied[0];
    }

    @Override
    public List<ProcessInstance> findProcessInstanceByProcessDefinitionId(String processDefinitionId, String tenantId) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
        List<ProcessInstance> instances = new ArrayList<>();

        collection.find(eq("processDefinitionId", processDefinitionId))
                .map(ProcessInstanceMapper::fromDocument)
                .into(instances);

        return instances;
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, String tenantId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        List<Bson> filters = new ArrayList<>();

        filters.add(eq("processDefinitionId", processDefinitionId));
        filters.add(eq("tenantId", tenantId));
        Bson finalFilter = and(filters);

        List<ExternalTask> externalTasks = new ArrayList<>();
        collection.find(finalFilter)
                .map(ExternalTaskMapper::fromDocument)
                .into(externalTasks);

        return externalTasks;
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        List<Bson> filters = new ArrayList<>();

        filters.add(eq("processDefinitionId", processDefinitionId));
        Bson finalFilter = and(filters);

        List<ExternalTask> externalTasks = new ArrayList<>();
        collection.find(finalFilter)
                .map(ExternalTaskMapper::fromDocument)
                .into(externalTasks);

        return externalTasks;
    }

    @Override
    public List<ExternalTask> findExternalTasksByProcessDefinitionId(String processDefinitionId, List<String> tenantIds) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        List<Bson> filters = new ArrayList<>();

        filters.add(eq("processDefinitionId", processDefinitionId));
        filters.add(in("tenantId", tenantIds));

        Bson finalFilter = and(filters);

        List<ExternalTask> externalTasks = new ArrayList<>();
        collection.find(finalFilter)
                .map(ExternalTaskMapper::fromDocument)
                .into(externalTasks);

        return externalTasks;
    }

    @Override
    public List<ExternalTask> findExternalTasksByAssignee(String assignee, String tenantId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);

        List<Bson> filters = new ArrayList<>();

        filters.add(eq("assignee", assignee));
        filters.add(eq("tenantId", tenantId));
        Bson finalFilter = and(filters);

        List<ExternalTask> externalTasks = new ArrayList<>();
        collection.find(finalFilter)
                .map(ExternalTaskMapper::fromDocument)
                .into(externalTasks);

        return externalTasks;
    }

    public void ensureIndexes() {


        getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION).createIndex(
                Indexes.compoundIndex(Indexes.ascending("key"), Indexes.descending("version")),
                new IndexOptions().name("key_version_idx")
        );

        getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION).createIndex(
                Indexes.compoundIndex(Indexes.ascending("key"), Indexes.ascending("checksum")),
                new IndexOptions().name("key_checksum_idx")
        );

        getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION).createIndex(
                Indexes.compoundIndex(Indexes.ascending("status"), Indexes.ascending("dueDate")),
                new IndexOptions().name("status_duedate_idx")
        );

        getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION).createIndex(
                Indexes.ascending("processInstanceId"),
                new IndexOptions().name("exec_task_proc_inst_idx")
        );

        // usado por getMetricsByNodeForProcessDefinition (SSE de /pulse) — antes deste índice, o $match inicial
        // da agregação por definição era COLLSCAN, repetido a cada tick do stream. Ver docs/engine/21-...md §4.7.
        getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION).createIndex(
                Indexes.ascending("processDefinitionId"),
                new IndexOptions().name("exec_task_proc_def_idx")
        );

        getDatabase().getCollection(INCIDENTS_COLLECTION).createIndex(
                Indexes.ascending("processInstanceId"),
                new IndexOptions().name("inc_proc_inst_idx")
        );


        // indice de tarefas externas por process instance id
        MongoCollection<Document> externalTaskCollection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
        externalTaskCollection.createIndex(Indexes.ascending("processInstanceId"), new IndexOptions().name("proc_inst_idx"));

        // indice de tarefas externas por process definition id e tenant
        externalTaskCollection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("processDefinitionId"), Indexes.ascending("tenantId")),
                new IndexOptions().name("proc_def_tenant_idx")
        );


        // indice de tarefas externas por assignee id e tenant
        externalTaskCollection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("assignee"), Indexes.ascending("tenantId")),
                new IndexOptions().name("assignee_tenant_idx")
        );

        // EVENT_CATCHER: busca O(1) por chave de correlação (correlateMessage) — sparse porque a maioria das
        // ExternalTasks não é um EVENT_CATCHER e não tem esse campo.
        externalTaskCollection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("correlationKey"), Indexes.ascending("tenantId")),
                new IndexOptions().name("correlation_key_tenant_idx").sparse(true)
        );

        // EVENT_CATCHER GROUP: usado pela cascata de limpeza de filhas em commitWork (deleteMany por
        // coordinatorTaskId) quando a tarefa-mãe é apagada.
        externalTaskCollection.createIndex(
                Indexes.ascending("coordinatorTaskId"),
                new IndexOptions().name("event_catcher_coordinator_idx").sparse(true)
        );

        // PROCESS_INSTANCE: sustenta POST /process-instances/search, o endpoint mais usado do monitor de
        // instâncias — até aqui a coleção não tinha nenhum índice, então todo filtro era COLLSCAN. Ver
        // docs/engine/21-revisao-observabilidade-e-performance-monitor.md §4.1.
        MongoCollection<Document> processInstanceCollection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);

        // combinação de filtro mais comum do monitor (tenant + status), já ordenado por startedAt (sort padrão
        // de listSummary()) para cobrir o caso mais frequente sem SORT em memória.
        processInstanceCollection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("tenantId"),
                        Indexes.ascending("status"),
                        Indexes.descending("startedAt")
                ),
                new IndexOptions().name("tenant_status_started_idx")
        );

        // filtro parentInstanceId (item 11 do doc de observabilidade) — "quais filhas tem essa instância" via
        // /process-instances/search. Sparse porque a maioria das instâncias não é filha de subprocesso.
        processInstanceCollection.createIndex(
                Indexes.ascending("parentInstanceId"),
                new IndexOptions().name("parent_instance_idx").sparse(true)
        );

        // callerTaskId — nível 0 da cascata de cancelamento recursivo de CALL_ACTIVITY_COORDINATOR (ver
        // docs/engine/20-subprocessos-call-activity-especificacao.md, §5, e o Javadoc de
        // cancelActiveChildSubtrees). Sparse pelo mesmo motivo de parentInstanceId — só instâncias filhas de
        // call activity têm esse campo.
        processInstanceCollection.createIndex(
                Indexes.ascending("callerTaskId"),
                new IndexOptions().name("caller_task_idx").sparse(true)
        );

        processInstanceCollection.createIndex(
                Indexes.ascending("businessKey"),
                new IndexOptions().name("business_key_idx").sparse(true)
        );

        processInstanceCollection.createIndex(
                Indexes.ascending("processDefinitionId"),
                new IndexOptions().name("proc_def_id_idx")
        );

        processInstanceCollection.createIndex(
                Indexes.descending("startedAt"),
                new IndexOptions().name("started_at_idx")
        );

        // activeNodeId filtra em activeNodes.<nodeId> — chave dinâmica (um nó de processo diferente por query).
        // Índice wildcard cobre qualquer nodeId sem precisar de um índice fixo por chave.
        processInstanceCollection.createIndex(
                Indexes.ascending("activeNodes.$**"),
                new IndexOptions().name("active_nodes_wildcard_idx")
        );

        // variableEquals/variableExists filtram em variables.<chave>.value — chave definida pelo usuário do
        // processo, mesmo motivo do índice acima. Não elimina o COLLSCAN de buscas parciais/deep search (essas
        // nunca existiram aqui), só cobre o match exato hoje suportado.
        processInstanceCollection.createIndex(
                Indexes.ascending("variables.$**"),
                new IndexOptions().name("variables_wildcard_idx")
        );

        // indices da coleção de outbox/histórico de eventos
        MongoCollection<Document> outboxEventsCollection = getDatabase().getCollection(OUTBOX_EVENTS_COLLECTION);
        outboxEventsCollection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("processInstanceId"), Indexes.ascending("timestamp")),
                new IndexOptions().name("proc_inst_timestamp_idx")
        );
        outboxEventsCollection.createIndex(
                Indexes.ascending("processDefinitionId"),
                new IndexOptions().name("proc_def_idx")
        );
        outboxEventsCollection.createIndex(
                Indexes.compoundIndex(Indexes.ascending("relayStatus"), Indexes.ascending("timestamp")),
                new IndexOptions().name("relay_status_timestamp_idx")
        );

        ensureOutboxTtlIndex(outboxEventsCollection);
    }

    /**
     * Cria (ou ajusta, via {@code collMod}) o índice TTL nativo do MongoDB sobre {@code outbox_events.timestamp}
     * quando {@code kikwiflow.outbox.ttl} está configurado. Se {@code outboxTtlSeconds <= 0} (padrão), a
     * retenção é indefinida e nenhum índice TTL é criado ou tocado — desabilitar o TTL depois de já tê-lo criado
     * não remove o índice existente automaticamente; isso exige um passo manual
     * ({@code db.outbox_events.dropIndex("outbox_ttl_idx")}).
     */
    private void ensureOutboxTtlIndex(MongoCollection<Document> outboxEventsCollection) {
        if (outboxTtlSeconds <= 0) {
            return;
        }

        String indexName = "outbox_ttl_idx";
        try {
            outboxEventsCollection.createIndex(
                    Indexes.ascending("timestamp"),
                    new IndexOptions().name(indexName).expireAfter(outboxTtlSeconds, TimeUnit.SECONDS)
            );
        } catch (MongoCommandException e) {
            // IndexOptionsConflict (85) / IndexKeySpecsConflict (86): o índice já existe com um
            // expireAfterSeconds diferente do configurado agora — ajusta em vez de falhar o boot.
            if (e.getErrorCode() == 85 || e.getErrorCode() == 86) {
                getDatabase().runCommand(new Document("collMod", OUTBOX_EVENTS_COLLECTION)
                        .append("index", new Document("name", indexName).append("expireAfterSeconds", outboxTtlSeconds)));
            } else {
                throw e;
            }
        }
    }


    @Override
    public ExternalTaskQuery createExternalTaskQuery() {
        return new MongoExternalTaskQuery();
    }

    @Override
    public Map<String, KKFMetrics> getMetricsByNodeForProcessDefinition(String processDefinitionId) {
        Map<String, KKFMetrics> metricsMap = new HashMap<>();

        List<Bson> executablePipeline = Arrays.asList(
                Aggregates.match(Filters.eq("processDefinitionId", processDefinitionId)),
                Aggregates.group("$taskDefinitionId",
                        Accumulators.sum("running", 1),
                        Accumulators.sum("failed", new Document("$cond", Arrays.asList(
                                new Document("$eq", Arrays.asList("$status", "ERROR")), 1, 0
                        )))
                )
        );

        getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION)
                .withReadPreference(ReadPreference.secondaryPreferred())
                .aggregate(executablePipeline)
                .forEach(doc -> {
                    String nodeId = doc.getString("_id");
                    long running = doc.getInteger("running", 0);
                    long failed = doc.getInteger("failed", 0);
                    metricsMap.put(nodeId, new KKFMetrics(running, 100.00, failed));
                });

        List<Bson> externalPipeline = Arrays.asList(
                // Exclui CORRELATED pelo mesmo motivo de countExternalTasksByDefinitionId: uma filha de
                // EVENT_CATCHER GROUP já correlacionada, aguardando só a limpeza em cascata quando a mãe
                // concluir, não é mais trabalho "em execução".
                Aggregates.match(Filters.and(
                        Filters.eq("processDefinitionId", processDefinitionId),
                        Filters.ne("status", ExternalTaskStatus.CORRELATED.name())
                )),
                Aggregates.group("$taskDefinitionId",
                        Accumulators.sum("running", 1)
                )
        );

        getDatabase().getCollection(EXTERNAL_TASK_COLLECTION)
                .withReadPreference(ReadPreference.secondaryPreferred())
                .aggregate(externalPipeline)
                .forEach(doc -> {
                    String nodeId = doc.getString("_id");
                    long running = doc.getInteger("running", 0);
                    metricsMap.put(nodeId, new KKFMetrics(running, 100.00, 0L));
                });

        return metricsMap;
    }

    @Override
    public Optional<ProcessDefinition> findByKeyAndChecksum(String key, String checksum) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);
        Document doc = collection.find(
                and(eq("key", key), eq("checksum", checksum))
        ).first();

        return Optional.ofNullable(doc)
                .map(ProcessDefinitionMapper::fromDocument);
    }

    @Override
    public Optional<ProcessDefinition> findLatestVersionByKey(String key) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);

        Document doc = collection.find(eq("key", key))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        return Optional.ofNullable(doc)
                .map(ProcessDefinitionMapper::fromDocument);
    }

    @Override
    public List<ExecutableTask> findExecutableTasksByProcessInstanceId(String processInstanceId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);
        List<ExecutableTask> tasks = new ArrayList<>();
        collection.find(eq("processInstanceId", processInstanceId))
                .map(ExecutableTaskMapper::fromDocument)
                .into(tasks);

        return tasks;
    }

    @Override
    public List<OutboxEventEntity> findEventHistoryByProcessInstanceId(String processInstanceId) {
        MongoCollection<Document> collection = getDatabase().getCollection(OUTBOX_EVENTS_COLLECTION);
        List<OutboxEventEntity> events = new ArrayList<>();
        collection.find(eq("processInstanceId", processInstanceId))
                .sort(Sorts.ascending("timestamp"))
                .map(OutboxEventMapper::fromDocument)
                .into(events);

        return events;
    }


    @Override
    public ProcessInstanceQuery createProcessInstanceQuery() {
        return new MongoProcessInstanceQuery();
    }

    private class MongoProcessInstanceQuery implements ProcessInstanceQuery {
        private final List<Bson> filters = new ArrayList<>();
        private int page = 0;
        private int size = 20;
        private Bson sort = Sorts.descending("startedAt");

        @Override
        public ProcessInstanceQuery processDefinitionId(String processDefinitionId) {
            if (processDefinitionId != null && !processDefinitionId.isBlank()) {
                filters.add(Filters.eq("processDefinitionId", processDefinitionId));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery processDefinitionIdIn(List<String> processDefinitionIds) {
            if (processDefinitionIds != null && !processDefinitionIds.isEmpty()) {
                filters.add(Filters.in("processDefinitionId", processDefinitionIds));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery processDefinitionKeyIn(List<String> processDefinitionKeys) {
            if (processDefinitionKeys != null && !processDefinitionKeys.isEmpty()) {
                List<String> resolvedDefIds = new ArrayList<>();
                getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION)
                        .find(Filters.in("key", processDefinitionKeys))
                        .projection(Projections.include("_id"))
                        .forEach(doc -> resolvedDefIds.add(doc.getString("_id")));

                if (resolvedDefIds.isEmpty()) {
                    filters.add(Filters.eq("_id", "no-match"));
                } else {
                    filters.add(Filters.in("processDefinitionId", resolvedDefIds));
                }
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery activeNodeId(String activeNodeId) {
            if (activeNodeId != null && !activeNodeId.isBlank()) {
                filters.add(Filters.gt("activeNodes." + activeNodeId, 0));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery parentInstanceId(String parentInstanceId) {
            if (parentInstanceId != null && !parentInstanceId.isBlank()) {
                filters.add(Filters.eq("parentInstanceId", parentInstanceId));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery tenantId(String tenantId) {
            if (tenantId != null && !tenantId.isBlank()) {
                filters.add(Filters.eq("tenantId", tenantId));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery tenantIdIn(List<String> tenantIds) {
            if (tenantIds != null && !tenantIds.isEmpty()) {
                filters.add(Filters.in("tenantId", tenantIds));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery statusIn(List<ProcessInstanceStatus> statuses) {
            if (statuses != null && !statuses.isEmpty()) {
                List<String> statusNames = statuses.stream().map(Enum::name).toList();
                filters.add(Filters.in("status", statusNames));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery businessKey(String businessKey) {
            if (businessKey != null && !businessKey.isBlank()) {
                filters.add(Filters.eq("businessKey", businessKey));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery businessKeyIn(List<String> businessKeys) {
            if (businessKeys != null && !businessKeys.isEmpty()) {
                filters.add(Filters.in("businessKey", businessKeys));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery startedAfter(Instant startedAfter) {
            if (startedAfter != null) {
                filters.add(Filters.gte("startedAt", startedAfter));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery startedBefore(Instant startedBefore) {
            if (startedBefore != null) {
                filters.add(Filters.lte("startedAt", startedBefore));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery variableEquals(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                String encodedKey = MongoKeyEncoder.encode(key);
                filters.add(Filters.eq("variables." + encodedKey + ".value", value));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery variableExists(String key) {
            if (key != null && !key.isBlank()) {
                String encodedKey = MongoKeyEncoder.encode(key);
                filters.add(Filters.exists("variables." + encodedKey, true));
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery orderBy(String field, boolean ascending) {
            if (field != null && !field.isBlank()) {
                // "id" é o nome de domínio (ProcessInstanceSummary.id()); "_id" é o detalhe de armazenamento do
                // Mongo. Essa tradução é responsabilidade desta implementação, não do chamador — o backend
                // in-memory usado pelos testes do motor não precisa dela, porque seu campo já se chama "id".
                String resolvedField = "id".equals(field) ? "_id" : field;
                this.sort = ascending ? Sorts.ascending(resolvedField) : Sorts.descending(resolvedField);
            }
            return this;
        }

        @Override
        public ProcessInstanceQuery page(int page) {
            this.page = Math.max(0, page);
            return this;
        }

        @Override
        public ProcessInstanceQuery size(int size) {
            // Defesa em profundidade: o mesmo limite já é aplicado em ProcessInstanceSearchRequest.
            // getOrDefaultSize() do lado do REST, mas qualquer outro chamador desta API (atual ou futuro)
            // também fica protegido de pedir uma página sem limite. Ver docs/engine/21-...md §4.5.
            this.size = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 20;
            return this;
        }

        @Override
        public PageResult<ProcessInstanceSummary> listSummary() {
            MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION)
                    .withReadPreference(ReadPreference.secondaryPreferred());
            Bson finalFilter = filters.isEmpty() ? new Document() : Filters.and(filters);

            long totalElements = collection.countDocuments(finalFilter);
            int totalPages = (int) Math.ceil((double) totalElements / size);

            Bson projection = Projections.include(
                    "_id",
                    "businessKey",
                    "status",
                    "processDefinitionId",
                    "startedAt",
                    "endedAt",
                    "activeNodes",
                    "parentInstanceId",
                    "callerTaskId",
                    "callerBranchId"
            );

            List<ProcessInstanceSummary> content = new ArrayList<>();

            collection.find(finalFilter)
                    .projection(projection)
                    .sort(sort)
                    .skip(page * size)
                    .limit(size)
                    .forEach(doc -> {
                        Map<String, Integer> activeNodesMap = new java.util.HashMap<>();
                        Document activeNodesDoc = doc.get("activeNodes", Document.class);
                        if (activeNodesDoc != null) {
                            activeNodesDoc.forEach((k, v) -> {
                                if (v instanceof Integer) activeNodesMap.put(k, (Integer) v);
                            });
                        }

                        content.add(new ProcessInstanceSummary(
                                doc.getString("_id"),
                                doc.getString("businessKey"),
                                ProcessInstanceStatus.valueOf(doc.getString("status")),
                                doc.getString("processDefinitionId"),
                                InstantMapper.mapToInstant("startedAt", doc),
                                InstantMapper.mapToInstant("endedAt", doc),
                                activeNodesMap,
                                doc.getString("parentInstanceId"),
                                doc.getString("callerTaskId"),
                                doc.getString("callerBranchId")
                        ));
                    });

            return new PageResult<>(content, totalElements, totalPages, page, size);
        }
    }


    /**
     * Implementação interna da API de query fluente para ExternalTasks.
     */
    private class MongoExternalTaskQuery implements ExternalTaskQuery {
        private final List<Bson> filters = new ArrayList<>();

        @Override
        public ExternalTaskQuery tenantId(String tenantId) {
            if (tenantId != null) {
                filters.add(eq("tenantId", tenantId));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery taskDefinitionId(String taskDefinitionId) {
            if (taskDefinitionId != null) {
                filters.add(eq("taskDefinitionId", taskDefinitionId));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery tenantIdIn(List<String> tenantIdIn) {
            if (tenantIdIn != null && !tenantIdIn.isEmpty()) {
                filters.add(in("tenantId", tenantIdIn));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery processInstanceId(String processInstanceId) {
            if (processInstanceId != null) {
                filters.add(eq("processInstanceId", processInstanceId));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery processDefinitionId(String processDefinitionId) {
            if (processDefinitionId != null) {
                filters.add(eq("processDefinitionId", processDefinitionId));
            }
            return this;
        }

        @Override
        public ExternalTaskQuery assignee(String assignee) {
            if (assignee != null) {
                filters.add(eq("assignee", assignee));
            }
            return this;
        }

        @Override
        public List<ExternalTask> list() {
            MongoCollection<Document> collection = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
            List<ExternalTask> tasks = new ArrayList<>();
            collection.find(buildFilter()).map(ExternalTaskMapper::fromDocument).into(tasks);
            return tasks;
        }


        @Override
        public long count() {
            return getDatabase().getCollection(EXTERNAL_TASK_COLLECTION).countDocuments(buildFilter());
        }

        private Bson buildFilter() {
            return filters.isEmpty() ? new Document() : and(filters);
        }
    }
}
