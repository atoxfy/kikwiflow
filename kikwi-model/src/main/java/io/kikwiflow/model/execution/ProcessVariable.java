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

package io.kikwiflow.model.execution;

import java.util.Set;

public record ProcessVariable(String name, Set<String> readRoles, Set<String> writeRoles, boolean isTransient, Object value) {
    public ProcessVariable(String name, Object value) {
        this(name, Set.of(), Set.of(), false, value);
    }

    public boolean canRead(Set<String> userRoles) {
        if (readRoles == null || readRoles.isEmpty()) return true;
        if (userRoles == null) return false;
        return userRoles.stream().anyMatch(readRoles::contains);
    }

    public boolean canWrite(Set<String> userRoles) {
        if (writeRoles == null || writeRoles.isEmpty()) return true;
        if (userRoles == null) return false;
        return userRoles.stream().anyMatch(writeRoles::contains);
    }
}
