/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
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
import io.kikwiflow.persistence.api.repository.KikwiEngineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TaskAcquirer — nó passivo (task-acquisition-enabled)")
class TaskAcquirerTest {

    private final KikwiEngineRepository repository = mock(KikwiEngineRepository.class);
    private final KikwiflowEngine engine = mock(KikwiflowEngine.class);
    private TaskAcquirer acquirer;

    private KikwiflowConfig config(boolean acquisitionEnabled) {
        KikwiflowConfig config = new KikwiflowConfig();
        config.setTaskAcquisitionIntervalMillis(20);
        config.setShutdownGracePeriodSeconds(1);
        config.setTaskAcquisitionEnabled(acquisitionEnabled);
        return config;
    }

    @AfterEach
    void stop() {
        if (acquirer != null) {
            acquirer.stop();
        }
    }

    @Test
    @DisplayName("Por padrão a aquisição de tarefas vem ligada")
    void enabledByDefault() {
        assertTrue(new KikwiflowConfig().isTaskAcquisitionEnabled());
    }

    @Nested
    @DisplayName("Dado um nó com a aquisição desligada")
    class Passive {

        @Test
        @DisplayName("Quando o acquirer sobe, Então nunca busca nem trava tarefas no banco")
        void neverPollsTheRepository() {
            acquirer = new TaskAcquirer(repository, config(false));

            acquirer.start(engine);

            verify(repository, after(200).never()).findAndLockDueTasks(any(), anyInt(), anyString(), anyLong());
            verify(engine, never()).executeFromTask(any());
        }
    }

    @Nested
    @DisplayName("Dado um nó com a aquisição ligada")
    class Active {

        @Test
        @DisplayName("Quando o acquirer sobe, Então busca tarefas vencidas com o id deste nó")
        void pollsTheRepository() {
            when(repository.findAndLockDueTasks(any(), anyInt(), anyString(), anyLong())).thenReturn(List.of());
            acquirer = new TaskAcquirer(repository, config(true));

            acquirer.start(engine);

            verify(repository, timeout(2000).atLeastOnce()).findAndLockDueTasks(any(), anyInt(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("Dado um repositório que falha em toda aquisição")
    class FailingRepository {

        @Test
        @DisplayName("Quando o poller roda, Então espera com backoff crescente em vez de girar sem pausa")
        void backsOffInsteadOfSpinning() {
            when(repository.findAndLockDueTasks(any(), anyInt(), anyString(), anyLong()))
                    .thenThrow(new NullPointerException());
            acquirer = new TaskAcquirer(repository, config(true));

            acquirer.start(engine);

            // Intervalo de 20 ms dobrando a cada falha: ~5 tentativas em 600 ms. Sem backoff seriam milhares.
            verify(repository, after(600).atMost(8)).findAndLockDueTasks(any(), anyInt(), anyString(), anyLong());
            verify(repository, atLeast(2)).findAndLockDueTasks(any(), anyInt(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Então o backoff dobra a cada falha seguida e para no teto")
        void backoffDoublesUpToTheCap() {
            assertEquals(5_000L, TaskAcquirer.errorBackoffMillis(5_000L, 1));
            assertEquals(10_000L, TaskAcquirer.errorBackoffMillis(5_000L, 2));
            assertEquals(40_000L, TaskAcquirer.errorBackoffMillis(5_000L, 4));
            assertEquals(TaskAcquirer.MAX_ERROR_BACKOFF_MILLIS, TaskAcquirer.errorBackoffMillis(5_000L, 5));
            assertEquals(TaskAcquirer.MAX_ERROR_BACKOFF_MILLIS, TaskAcquirer.errorBackoffMillis(5_000L, 1_000));
        }

        @Test
        @DisplayName("Então um intervalo configurado acima do teto é respeitado como piso")
        void intervalAboveCapIsKept() {
            assertEquals(120_000L, TaskAcquirer.errorBackoffMillis(120_000L, 3));
        }
    }
}
