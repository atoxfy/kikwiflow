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
package io.kikwiflow.model.execution.form;

import java.util.List;

/**
 * Descrição, gerada pelo motor, dos campos que um frontend deve renderizar para iniciar um processo
 * ({@code nodeId} = start event) ou concluir uma {@code EXTERNAL_TASK}. Os campos vêm dos {@code inputs} do
 * nó, na ordem declarada.
 */
public record FormDescriptor(String processDefinitionId,
                             String processDefinitionKey,
                             String nodeId,
                             String nodeName,
                             List<FormField> fields) {

    public FormDescriptor {
        fields = fields != null ? List.copyOf(fields) : List.of();
    }
}
