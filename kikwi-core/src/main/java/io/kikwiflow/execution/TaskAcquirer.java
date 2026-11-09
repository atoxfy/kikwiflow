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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TaskAcquirer implements Runnable {
    private KikwiflowEngine engine;
    private final KikwiEngineRepository kikwiEngineRepository;
    private final KikwiflowConfig kikwiflowConfig;
    private final ExecutorService acquirerExecutor;
    private final ExecutorService workerExecutor;
    private volatile boolean running = false;
    private final Semaphore concurrencyLimit;
    private final String workerId;

    public TaskAcquirer(KikwiEngineRepository kikwiEngineRepository, KikwiflowConfig kikwiflowConfig) {
        this.kikwiEngineRepository = kikwiEngineRepository;
        this.kikwiflowConfig = kikwiflowConfig;
        this.acquirerExecutor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("kikwiflow-acquirer-", 0).factory());
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.concurrencyLimit = new Semaphore(kikwiflowConfig.getMaxConcurrentTasks());
        String baseName = kikwiflowConfig.getInstanceName() != null ? kikwiflowConfig.getInstanceName() : "kikwi-node";
        this.workerId = baseName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }


    public void start(KikwiflowEngine kikwiflowEngine){
        this.engine = kikwiflowEngine;
        if(!kikwiflowConfig.isTaskAcquisitionEnabled()){
            System.out.println("Kikwiflow Task Acquirer desligado (task-acquisition-enabled=false): nó passivo, nenhuma tarefa será executada por esta instância.");
            return;
        }
        if(!running){
            this.running = true;
            this.acquirerExecutor.submit(this);
            System.out.println("Kikwiflow Task Acquirer started... " + kikwiflowConfig.toString());
        }
    }


    public void stop() {
        this.running = false;
        System.out.println("Kikwiflow Task Acquirer: Iniciando graceful shutdown...");

        this.acquirerExecutor.shutdown();
        this.workerExecutor.shutdown();

        long gracePeriod = kikwiflowConfig.getShutdownGracePeriodSeconds();

        try {
            if (!this.acquirerExecutor.awaitTermination(gracePeriod, TimeUnit.SECONDS)) {
                System.err.println("Kikwiflow Task Acquirer: Acquirer timeout. Forçando parada.");
                this.acquirerExecutor.shutdownNow();
            }

            if (!this.workerExecutor.awaitTermination(gracePeriod, TimeUnit.SECONDS)) {
                System.err.println("Kikwiflow Task Acquirer: Workers timeout. Existem tarefas que foram interrompidas abruptamente.");
                this.workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Kikwiflow Task Acquirer: Shutdown interrompido externamente.");
            this.acquirerExecutor.shutdownNow();
            this.workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Kikwiflow Task Acquirer stopped.");
    }

    /** Teto do backoff do poller quando a aquisição falha seguidamente (ex.: Mongo fora, documento corrompido). */
    static final long MAX_ERROR_BACKOFF_MILLIS = 60_000L;

    /**
     * Espera após {@code consecutiveErrors} falhas seguidas: o intervalo normal dobrando a cada falha, até
     * {@link #MAX_ERROR_BACKOFF_MILLIS}. Sem isso o loop voltaria imediatamente ao repositório que acabou de falhar.
     */
    static long errorBackoffMillis(long intervalMillis, int consecutiveErrors) {
        long base = Math.max(intervalMillis, 1L);
        int shift = Math.min(Math.max(consecutiveErrors - 1, 0), 20);
        return Math.min(base << shift, Math.max(base, MAX_ERROR_BACKOFF_MILLIS));
    }

    @Override
    public void run() {
        int consecutiveErrors = 0;
        while (running) {
            try {
                int availablePermits = concurrencyLimit.availablePermits();

                if (availablePermits <= 0) {
                    Thread.sleep(kikwiflowConfig.getTaskAcquisitionIntervalMillis());
                    continue;
                }

                int limitToFetch = Math.min(availablePermits, kikwiflowConfig.getTaskAcquisitionMaxTasks());
                List<ExecutableTask> taskList = kikwiEngineRepository.findAndLockDueTasks(
                        Instant.now(),
                        limitToFetch,
                        this.workerId,
                        this.kikwiflowConfig.getLockTimeoutMillis()
                );

                for (ExecutableTask task : taskList) {
                    concurrencyLimit.acquireUninterruptibly();
                    workerExecutor.submit(() -> {
                        try {
                            engine.executeFromTask(task);
                        } catch (Exception ex) {
                            System.err.println("Erro crítico na execução da task " + task.id() + ": " + ex);
                            ex.printStackTrace();
                        } finally {
                            concurrencyLimit.release();
                        }
                    });
                }

                if (consecutiveErrors > 0) {
                    System.out.println("Kikwiflow Poller recuperado após " + consecutiveErrors + " falha(s) seguida(s).");
                    consecutiveErrors = 0;
                }
                Thread.sleep(kikwiflowConfig.getTaskAcquisitionIntervalMillis());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.running = false;
            } catch (Exception e) {
                consecutiveErrors++;
                long backoff = errorBackoffMillis(kikwiflowConfig.getTaskAcquisitionIntervalMillis(), consecutiveErrors);
                // toString() traz a classe: getMessage() sozinho imprimia só "null" (ex.: NPE sem mensagem).
                System.err.println("Erro no Poller (falha " + consecutiveErrors + " seguida; nova tentativa em "
                        + backoff + " ms): " + e);
                if (consecutiveErrors == 1) {
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    this.running = false;
                }
            }
        }
    }
}
