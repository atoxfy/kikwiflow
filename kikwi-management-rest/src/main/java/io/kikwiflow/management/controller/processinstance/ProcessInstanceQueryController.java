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

package io.kikwiflow.management.controller.processinstance;

import io.kikwiflow.api.dto.CountResponse;
import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.management.dtos.ProcessInstanceSearchRequest;
import io.kikwiflow.management.dtos.ProcessInstanceSnapshot;
import io.kikwiflow.management.exception.NotFoundException;
import io.kikwiflow.management.exception.NotImplementedException;
import io.kikwiflow.management.mapper.ProcessInstanceQueryMapper;
import io.kikwiflow.management.service.ProcessInstanceSnapshotService;
import io.kikwiflow.model.execution.Incident;
import io.kikwiflow.model.execution.ProcessInstance;
import io.kikwiflow.model.execution.ProcessInstanceSummary;
import io.kikwiflow.model.shared.PageResult;
import io.kikwiflow.persistence.api.query.ProcessInstanceQuery;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import io.kikwiflow.spring.rest.api.query.ProcessInstanceQueryRestApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@KikwiRestController
@ConditionalOnBean(QueryRepository.class)
public class ProcessInstanceQueryController implements ProcessInstanceQueryRestApi {

    private final QueryRepository queryRepository;
    private final ProcessInstanceSnapshotService snapshotService;

    public ProcessInstanceQueryController(QueryRepository queryRepository, ProcessInstanceSnapshotService snapshotService) {
        this.queryRepository = queryRepository;
        this.snapshotService = snapshotService;
    }

    @GetMapping("/{id}/snapshot")
    public ProcessInstanceSnapshot getProcessInstanceSnapshot(@PathVariable("id") String id) {
        return snapshotService.getSnapshot(id);
    }

    @Override
    public CountResponse count(String processDefinitionId) {
        if(processDefinitionId != null){
            long count = queryRepository.countProcessInstancesByProcessDefinition(processDefinitionId);
            return new CountResponse(count);

        }

        throw new NotImplementedException("Not implemented");
    }

    @Override
    public ProcessInstance findProcessInstanceById(String id) {
        return queryRepository.findProcessInstanceById(id)
                .orElseThrow(() -> new NotFoundException("Process Instance Not Found"));
    }

    @Override
    public List<Incident> getIncidents(String id) {
        return queryRepository.findIncidentsByProcessInstanceId(id);
    }

    @Override
    public List<ProcessInstance> findAll(List<String> ids, String processDefinitionId, String tenantId) {
        if(ids != null && !ids.isEmpty()){
            return queryRepository.findProcessInstancesByIdIn(ids);
        }else if(processDefinitionId != null && tenantId != null){
            return queryRepository.findProcessInstanceByProcessDefinitionId(processDefinitionId, tenantId);
        }

        throw new NotImplementedException("");
    }


    @Deprecated
    @GetMapping("/summary")
    public PageResult<ProcessInstanceSummary> getProcessInstancesSummary(
            @RequestParam(required = false) String processDefinitionId,
            @RequestParam(required = false) String activeNodeId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryRepository.createProcessInstanceQuery()
                .processDefinitionId(processDefinitionId)
                .activeNodeId(activeNodeId)
                .tenantId(tenantId)
                .page(page)
                .size(size)
                .listSummary();
    }

    @PostMapping("/search")
    public ResponseEntity<PageResult<ProcessInstanceSummary>> searchProcessInstances(
            @RequestBody ProcessInstanceSearchRequest searchRequest) {

        ProcessInstanceQuery baseQuery = queryRepository.createProcessInstanceQuery();
        PageResult<ProcessInstanceSummary> result = ProcessInstanceQueryMapper
                .applyRequest(baseQuery, searchRequest)
                .listSummary();

        return ResponseEntity.ok(result);
    }
}
