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

package io.kikwiflow.model.event;

/**
 * Catálogo dos tipos de {@link CriticalEvent} conhecidos pelo motor. É a fonte única de verdade
 * usada tanto para construir {@link OutboxEventEntity} (em vez de literais de string soltos pelo
 * código) quanto para dispatch polimórfico na (de)serialização de persistência.
 */
public enum CriticalEventType {
    FLOW_NODE_FINISHED(FlowNodeFinished.class),
    GATEWAY_ANSWER_RESOLVED(GatewayAnswerResolved.class),
    PROCESS_INSTANCE_FINISHED(ProcessInstanceFinished.class),
    PROCESS_INSTANCE_STARTED(ProcessInstanceStarted.class),
    INCIDENT_CREATED(IncidentCreated.class),
    INCIDENT_RESOLVED(IncidentResolved.class),
    EXTERNAL_TASK_CLAIMED(ExternalTaskClaimed.class),
    EXTERNAL_TASK_UNCLAIMED(ExternalTaskUnclaimed.class),
    EXTERNAL_TASK_COMPLETED(ExternalTaskCompleted.class),
    RETRY_SCHEDULED(RetryScheduled.class),
    PROCESS_VARIABLE_CHANGED(ProcessVariableChanged.class),
    TIMER_FIRED(TimerFired.class);

    private final Class<? extends CriticalEvent> payloadType;

    CriticalEventType(Class<? extends CriticalEvent> payloadType) {
        this.payloadType = payloadType;
    }

    public Class<? extends CriticalEvent> payloadType() {
        return payloadType;
    }
}
