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

package io.kikwiflow.security.api;

import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.security.IdentityContext;

import java.util.Map;
import java.util.Set;

public interface VariableSecurityPolicyManager {

    boolean canWrite(String processDefinitionId, IdentityContext identity, Set<String> variableNames);
    boolean canRead(String processDefinitionId, IdentityContext identity, Set<String> variableNames);
    boolean canWrite(String processDefinitionId, IdentityContext identity, String variableName);
    boolean canRead(String processDefinitionId, IdentityContext identity, String variableNames);
    Map<String, ProcessVariable> applyReadPoliciesAndMasking(String processDefinitionId, IdentityContext identity, Map<String, ProcessVariable> rawVariables);
}
