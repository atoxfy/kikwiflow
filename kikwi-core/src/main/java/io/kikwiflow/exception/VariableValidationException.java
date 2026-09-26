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
package io.kikwiflow.exception;

import io.kikwiflow.variable.VariableValidationError;

import java.util.List;

/**
 * Lançada quando as variáveis de um start ou de um complete de {@code EXTERNAL_TASK} não satisfazem os
 * {@code inputs} declarados no nó. Nada é persistido quando ela ocorre.
 */
public class VariableValidationException extends RuntimeException {

    private final String nodeId;
    private final List<VariableValidationError> errors;

    public VariableValidationException(String nodeId, List<VariableValidationError> errors) {
        super(String.format("Variable validation failed for node '%s': %s", nodeId,
                errors.stream().map(e -> e.variable() + "=" + e.code()).toList()));
        this.nodeId = nodeId;
        this.errors = List.copyOf(errors);
    }

    public String getNodeId() {
        return nodeId;
    }

    public List<VariableValidationError> getErrors() {
        return errors;
    }
}
