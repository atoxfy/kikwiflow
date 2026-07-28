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
import com.mongodb.client.result.UpdateResult;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.BranchPullIntention;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.enumerated.ExecutableTaskStatus;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
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

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;

public class MongoKikwiEngineRepository implements KikwiEngineRepository {
    
    private final String PROCESS_DEFINITION_COLLECTION = "process_definitions";
    private final String PROCESS_INSTANCE_COLLECTION = "process_instances";
    private final String EXTERNAL_TASK_COLLECTION = "external_tasks";
    private final String EXECUTABLE_TASK_COLLECTION = "executable_tasks";
    private final String INCIDENTS_COLLECTION = "incidents";

    private final MongoClient mongoClient;
    private final String databaseName;

    public MongoKikwiEngineRepository(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
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
        return getDatabase().getCollection(EXTERNAL_TASK_COLLECTION).countDocuments(
                eq("taskDefinitionId", id)
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

                List<WriteModel<Document>> executableTaskWrites = new ArrayList<>();
                if (unitOfWork.executableTasksToCreate() != null && !unitOfWork.executableTasksToCreate().isEmpty()) {
                    unitOfWork.executableTasksToCreate().forEach(task ->
                            executableTaskWrites.add(new InsertOneModel<>(ExecutableTaskMapper.toDocument(task)))
                    );
                }
                if (unitOfWork.executableTasksToDelete() != null && !unitOfWork.executableTasksToDelete().isEmpty()) {
                    executableTaskWrites.add(new DeleteManyModel<>(in("_id", unitOfWork.executableTasksToDelete())));
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

                        executableTasks.updateOne(clientSession, filter, updatePipeline);
                    }
                }

                if(unitOfWork.events() != null){
                    // TODO: Lidar com a persistência de Outbox Events
                }

                return "Transaction committed";
            });
        }
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
    public ProcessInstance addVariables(String processInstanceId, Map<String, ProcessVariable> variables) {
        if (variables == null || variables.isEmpty()) {
            return findProcessInstanceById(processInstanceId).orElse(null);
        }

        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);

        List<Bson> updates = new ArrayList<>();
        for (Map.Entry<String, ProcessVariable> entry : variables.entrySet()) {
            String fieldPath = "variables." + MongoKeyEncoder.encode(entry.getKey());
            updates.add(Updates.set(fieldPath, ProcessVariableMapper.toDocument(entry.getValue())));
        }

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Document updatedDoc = collection.findOneAndUpdate(eq("_id", processInstanceId), Updates.combine(updates), options);

        return ProcessInstanceMapper.fromDocument(updatedDoc);
    }

    @Override
    public void claim(String externalTaskId, String assignee) {
        MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
        externalTasks.updateOne(eq("_id", externalTaskId), Updates.set("assignee", assignee));
    }

    @Override
    public void unclaim(String externalTaskId) {
        MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
        externalTasks.updateOne(eq("_id", externalTaskId), Updates.unset("assignee"));
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

                // TODO: Considerar se os eventos do outbox e o histórico também devem ser deletados.
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
                Aggregates.match(Filters.eq("processDefinitionId", processDefinitionId)),
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
                this.sort = ascending ? Sorts.ascending(field) : Sorts.descending(field);
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
            this.size = size > 0 ? size : 20;
            return this;
        }

        @Override
        public PageResult<ProcessInstanceSummary> listSummary() {
            MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
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
                    "activeNodes"
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
                                activeNodesMap
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
