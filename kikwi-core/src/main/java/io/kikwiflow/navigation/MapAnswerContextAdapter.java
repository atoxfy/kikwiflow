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

package io.kikwiflow.navigation;

import io.kikwiflow.decision.api.AnswerContext;
import io.kikwiflow.model.execution.ProcessVariable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class MapAnswerContextAdapter implements AnswerContext {

    private final String processInstanceId;
    private final Map<String, Object> readOnlyVariables;

    public MapAnswerContextAdapter(String processInstanceId, Map<String, ProcessVariable> variables) {
        this.processInstanceId = processInstanceId;
        this.readOnlyVariables = Collections.unmodifiableMap(
                variables.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue() != null ? entry.getValue().value() : null
                        ))
        );
    }

    @Override
    public String getProcessInstanceId() {
        return processInstanceId;
    }

    @Override
    public Optional<Object> getVariableValue(String name) {
        return Optional.ofNullable(readOnlyVariables.get(name));
    }

    @Override
    public Map<String, Object> getVariables() {
        return readOnlyVariables;
    }
}