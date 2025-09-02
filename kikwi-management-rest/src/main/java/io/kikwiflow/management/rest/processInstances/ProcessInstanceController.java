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
package io.kikwiflow.management.rest.processInstances;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.query.api.ExternalTaskQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/process-instance")
public class ProcessInstanceController {

    private final KikwiflowEngine commandEngine;

    public ProcessInstanceController(KikwiflowEngine commandEngine) {
        this.commandEngine = commandEngine;
    }

    @PostMapping("/start/{processKey}")
    public ResponseEntity<ProcessInstance> startProcess(@PathVariable String processKey) {
        ProcessInstance instance = commandEngine.startProcess()
                .byKey(processKey)
                .withBusinessKey("test-bk-" + UUID.randomUUID())
                .execute();

        return ResponseEntity.ok(instance);
    }



}