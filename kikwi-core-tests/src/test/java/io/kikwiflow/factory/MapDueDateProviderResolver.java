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

import io.kikwiflow.execution.api.provider.DueDateProvider;
import io.kikwiflow.execution.api.resolver.DueDateProviderResolver;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test double de {@link DueDateProviderResolver} — resolve por nome a partir de um mapa em memória, registrado
 * pelo próprio teste via {@link SingletonsFactory.EngineHarness#withDueDateProvider(String, DueDateProvider)}.
 */
public class MapDueDateProviderResolver implements DueDateProviderResolver {

    private final Map<String, DueDateProvider> providers = new HashMap<>();

    public void register(String name, DueDateProvider provider) {
        providers.put(name, provider);
    }

    @Override
    public Optional<DueDateProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }
}
