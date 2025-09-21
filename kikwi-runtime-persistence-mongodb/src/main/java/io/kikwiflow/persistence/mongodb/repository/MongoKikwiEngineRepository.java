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

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import io.kikwiflow.persistence.mongodb.mapper.ExecutableTaskMapper;
import io.kikwiflow.persistence.mongodb.mapper.ExternalTaskMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessDefinitionMapper;
import io.kikwiflow.persistence.mongodb.mapper.ProcessInstanceMapper;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lte;

public class MongoKikwiEngineRepository implements KikwiEngineRepository {
    
    private final String PROCESS_DEFINITION_COLLECTION = "process_definitions";
    private final String PROCESS_INSTANCE_COLLECTION = "process_instances";
    private final String EXTERNAL_TASK_COLLECTION = "external_tasks";
    private final String EXECUTABLE_TASK_COLLECTION = "executable_tasks";

    
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
    public ProcessInstance saveProcessInstance(ProcessInstance instance) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
        Document doc = ProcessInstanceMapper.toDocument(instance);
        collection.replaceOne(eq("_id", instance.id()), doc, new ReplaceOptions().upsert(true));
        return  instance;
    }

    @Override
    public ProcessDefinition saveProcessDefinition(ProcessDefinition processDefinitionDeploy) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_DEFINITION_COLLECTION);
        Document doc = ProcessDefinitionMapper.toDocument(processDefinitionDeploy);
        collection.replaceOne(eq("_id", processDefinitionDeploy.id()), doc, new ReplaceOptions().upsert(true));
        return processDefinitionDeploy;
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

    @Override
    public void commitWork(UnitOfWork unitOfWork) {
        try (ClientSession clientSession = mongoClient.startSession()) {
            clientSession.withTransaction(() -> {
                MongoCollection<Document> processInstances = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
                MongoCollection<Document> externalTasks = getDatabase().getCollection(EXTERNAL_TASK_COLLECTION);
                MongoCollection<Document> executableTasks = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);

                if (unitOfWork.instanceToUpdate() != null) {
                    Document instanceDoc = ProcessInstanceMapper.toDocument(unitOfWork.instanceToUpdate());
                    processInstances.replaceOne(clientSession, eq("_id", unitOfWork.instanceToUpdate().id()), instanceDoc);
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
                
                if (!executableTaskWrites.isEmpty()) {
                    executableTasks.bulkWrite(clientSession, executableTaskWrites);
                }

                // TODO: Lidar com a persistência de Outbox Events

                return "Transaction committed";
            });
        }
    }

    @Override
    public List<ExecutableTask> findAndLockDueTasks(Instant now, int limit, String workerId) {
        MongoCollection<Document> collection = getDatabase().getCollection(EXECUTABLE_TASK_COLLECTION);
        List<ExecutableTask> lockedTasks = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            Bson filter = and(
                    eq("status", "PENDING"),
                    lte("dueDate", now)
            );

            Bson update = Updates.combine(
                    Updates.set("status", "LOCKED"),
                    Updates.set("executorId", workerId),
                    Updates.set("acquiredAt", Instant.now())
            );

            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                    .returnDocument(ReturnDocument.AFTER)
                    .sort(Sorts.ascending("dueDate"));

            Document lockedDoc = collection.findOneAndUpdate(filter, update, options);

            if (lockedDoc == null) {
                break;
            }

            lockedTasks.add(ExecutableTaskMapper.fromDocument(lockedDoc));
        }

        return lockedTasks;
    }

    @Override
    public Optional<ProcessInstance> findProcessInstanceById(String processInstanceId) {
        MongoCollection<Document> collection = getDatabase().getCollection(PROCESS_INSTANCE_COLLECTION);
        
        Document doc = collection.find(eq("_id", processInstanceId)).first();

        return Optional.ofNullable(doc)
                .map(ProcessInstanceMapper::fromDocument);
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
}
