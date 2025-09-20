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
package io.kikwiflow.management.rest.processDefinitions;

import io.kikwiflow.KikwiflowEngine;
import io.kikwiflow.management.rest.processDefinitions.dto.ProcessStagesView;
import io.kikwiflow.model.definition.process.ProcessDefinition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/process-definitions")
public class ProcessDefinitionController {

    private final KikwiflowEngine engine;
    private final ProcessDefinitionViewService viewService;

    public ProcessDefinitionController(KikwiflowEngine engine, ProcessDefinitionViewService viewService) {
        this.engine = engine;
        this.viewService = viewService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessDefinition> deploy(@RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(engine.deployDefinition(file.getInputStream()));
    }

    @GetMapping("/{key}/stages")
    public ResponseEntity<ProcessStagesView> getProcessStages(@PathVariable String key) {
        return viewService.getHumanTaskStages(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}