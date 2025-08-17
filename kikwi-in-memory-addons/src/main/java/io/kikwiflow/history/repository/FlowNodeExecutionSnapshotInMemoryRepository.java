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

package io.kikwiflow.history.repository;

import io.kikwiflow.model.event.FlowNodeExecuted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FlowNodeExecutionSnapshotInMemoryRepository implements FlowNodeExecutionSnapshotRepository {

    private Map<String, List<FlowNodeExecuted>> coveredElements = new HashMap<String, List<FlowNodeExecuted>>();


    @Override
    public void save(FlowNodeExecuted flowNodeExecutionSnapshot) {
        String processInstanceId = flowNodeExecutionSnapshot.getProcessInstanceId();

        List<FlowNodeExecuted> processInstanceIdElements = coveredElements.get(processInstanceId);
        if(Objects.isNull(processInstanceIdElements)){
            processInstanceIdElements = new ArrayList<>();
        }

        processInstanceIdElements.add(flowNodeExecutionSnapshot);
        coveredElements.put(processInstanceId, processInstanceIdElements);
    }
}
