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

package io.kikwiflow.management.rest.controller.navigation;


import io.kikwiflow.execution.dto.Continuation;
import io.kikwiflow.management.rest.controller.navigation.request.SimulateContinuationRequest;
import io.kikwiflow.management.rest.mapper.VariablesMapper;
import io.kikwiflow.model.definition.process.elements.FlowNodeDefinition;
import io.kikwiflow.navigation.Navigator;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/navigation")
public class NavigationController {

    private final Navigator navigator;
    private final QueryRepository queryRepository;

    public NavigationController(Navigator navigator, QueryRepository queryRepository) {
        this.navigator = navigator;
        this.queryRepository = queryRepository;
    }

    @PostMapping("/{process-definition-id}/{node-definition-id}/simulate-continuation")
    public ResponseEntity<Continuation> getContinuation(@PathVariable String processDefinitionId, @PathVariable String flowNodeDefinitionId, @RequestBody SimulateContinuationRequest simulateContinuationRequest) {
        return queryRepository.findProcessDefinitionById(processDefinitionId)
                .map(processDefinition -> {
                    FlowNodeDefinition flowNodeDefinition = processDefinition.flowNodes().get(flowNodeDefinitionId);
                    if(Objects.isNull(flowNodeDefinition)) throw new RuntimeException("FlowNodeDefinition not found");
                    Continuation continuation = navigator.determineNextContinuation(flowNodeDefinition, processDefinition, VariablesMapper.map(simulateContinuationRequest.variables()), false, null);
                    return ResponseEntity.ok(continuation);
                })
                .orElseThrow();
    }
}
