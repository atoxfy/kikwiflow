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

package io.kikwiflow.factory;

import io.kikwiflow.execution.TaskHandlerResolver;
import io.kikwiflow.execution.api.handler.TaskHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test double de {@link TaskHandlerResolver} — resolve por nome a partir de um mapa em memória, registrado
 * pelo próprio teste via {@link SingletonsFactory.EngineHarness#withTaskHandler(String, TaskHandler)}, em vez
 * de descoberta de beans Spring.
 */
public class MapTaskHandlerResolver implements TaskHandlerResolver {

    private final Map<String, TaskHandler> handlers = new HashMap<>();

    public void register(String name, TaskHandler handler) {
        handlers.put(name, handler);
    }

    @Override
    public Optional<TaskHandler> resolve(String beanName) {
        return Optional.ofNullable(handlers.get(beanName));
    }
}
