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

package io.kikwiflow.assertion;

import io.kikwiflow.model.event.CriticalEventType;
import io.kikwiflow.model.event.OutboxEventEntity;
import io.kikwiflow.model.event.ProcessInstanceFinished;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.enumerated.ProcessInstanceStatus;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.persistence.api.data.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class AssertableKikwiEngineTest {

    private AssertableKikwiEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AssertableKikwiEngine();
    }

    @Test
    void assertsProcessInstanceIsActiveAfterBeingSeeded() {
        engine.saveProcessInstance(activeInstance("proc-1"));

        engine.assertThatProcessInstanceIsActive("proc-1");
    }

    @Test
    void assertsProcessInstanceNotExistsAndIsCompletedAfterDeletion() {
        engine.saveProcessInstance(activeInstance("proc-2"));

        engine.deleteProcessInstanceById("proc-2");

        engine.assertThatProcessInstanceNotExistsInRuntimeContext("proc-2");
        engine.assertThatProcessInstanceIsCompleted("proc-2");
    }

    @Test
    void assertsHasAndHasNotActiveExternalTask() {
        ExternalTask task = ExternalTask.builder()
                .id("ext-1")
                .processInstanceId("proc-3")
                .taskDefinitionId("WAIT_FOR_INPUT")
                .build();

        UnitOfWork uow = new UnitOfWork(null, null, null,
                null, List.of(task), null, null, null, null, null, null, null, null, null, null);
        engine.commitWork(uow);

        engine.assertHasActiveExternalTaskOn("proc-3", "WAIT_FOR_INPUT");
        engine.assertHasntActiveExternalTaskOn("proc-3", "SOME_OTHER_NODE");
    }

    @Test
    void assertsProcessInstanceInHistoryAfterEventDrained() {
        ProcessInstance instance = activeInstance("proc-4");

        ProcessInstanceFinished finished = ProcessInstanceFinished.builder()
                .id("proc-4")
                .businessKey(instance.businessKey())
                .processDefinitionId(instance.processDefinitionId())
                .status(ProcessInstanceStatus.COMPLETED)
                .variables(instance.variables())
                .build();

        OutboxEventEntity event = new OutboxEventEntity(CriticalEventType.PROCESS_INSTANCE_FINISHED, finished);
        UnitOfWork uow = new UnitOfWork(null, null, null,
                null, null, null, null, null, List.of(event), null, null, null, null, null, null);

        engine.commitWork(uow);
        engine.evaluateEvents();

        ProcessInstance completedSnapshot = ProcessInstance.builder()
                .id("proc-4")
                .businessKey(instance.businessKey())
                .processDefinitionId(instance.processDefinitionId())
                .status(ProcessInstanceStatus.COMPLETED)
                .variables(instance.variables())
                .build();

        engine.assertIfHasProcessInstanceInHistory(completedSnapshot);
    }

    private ProcessInstance activeInstance(String id) {
        return ProcessInstance.builder()
                .id(id)
                .businessKey("BK-" + id)
                .processDefinitionId("def-a")
                .status(ProcessInstanceStatus.ACTIVE)
                .startedAt(Instant.now())
                .variables(Map.of())
                .build();
    }
}
