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

package io.kikwiflow.execution;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.config.KikwiflowConfig;
import io.kikwiflow.model.execution.node.ExecutableTask;
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TaskAcquirer implements Runnable{
    private final KikwiflowEngine engine;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final KikwiflowConfig kikwiflowConfig;
    private final ExecutorService executorService;
    private volatile boolean running = false;

    public TaskAcquirer(KikwiflowEngine engine, KikwiEngineRepository kikwiEngineRepository, KikwiflowConfig kikwiflowConfig) {
        this.engine = engine;
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.kikwiflowConfig = kikwiflowConfig;
        this.executorService = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("kikwiflow-task-acquirer-", 0).factory());
    }


    public void start(){
        if(!running){
            this.running = true;
            this.executorService.submit(this);
            System.out.println("Kikwiflow Task Acquirer started.");
        }
    }


    public void stop(){
        this.running = false;
        this.executorService.shutdown();
        try{
            if(!this.executorService.awaitTermination(10, TimeUnit.SECONDS)){
                this.executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            this.executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Kikwiflow Task Acquirer stopped.");
    }

    @Override
    public void run() {
        while (running){
            try {
                List<ExecutableTask> taskList = kikwiEngineRepository.findAndLockDueTasks(Instant.now(), kikwiflowConfig.getTaskAcquisitionMaxTasks(), "kikwiflow-1");
                for(ExecutableTask task : taskList){
                    engine.executeFromTask(task);
                }
                Thread.sleep(kikwiflowConfig.getTaskAcquisitionIntervalMillis());
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                this.running = false;
            } catch (Exception e){
                System.err.println("Errror during job acquisition: " + e.getMessage());
            }
        }
    }
}
