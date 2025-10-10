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

package io.kikwiflow.management.rest.controller.tasks;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessVariable;
import io.kikwiflow.model.execution.node.ExternalTask;
import io.kikwiflow.query.api.ExternalTaskQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final ExternalTaskQueryService queryService;
    private final KikwiflowEngine commandEngine;

    public TaskController(ExternalTaskQueryService queryService, KikwiflowEngine commandEngine) {
        this.queryService = queryService;
        this.commandEngine = commandEngine;
    }

    @GetMapping
    public ResponseEntity<List<ExternalTask>> findTasks(@RequestParam String processInstanceId) {
        List<ExternalTask> tasks = queryService.findByProcessInstanceId(processInstanceId);
        if (tasks.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(tasks);
    }


    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ProcessInstance> completeTask(@PathVariable String taskId, @RequestBody(required = false) Map<String, ProcessVariable> variables) {
        ProcessInstance instance = commandEngine.completeExternalTask(taskId, null, variables, null);
        return ResponseEntity.ok(instance);
    }
}
