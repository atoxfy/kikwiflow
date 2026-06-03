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

package io.kikwiflow.management.controller.stats;

import io.kikwiflow.management.controller.stats.response.KKFProcess;
import io.kikwiflow.management.service.StatsService;
import io.kikwiflow.persistence.api.repository.QueryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnBean(QueryRepository.class)
@RequestMapping("${kikwiflow.api.base-path:/engine/api/v1}/pulse")
public class StatsQueryController {

    private final StatsService statsService;

    public StatsQueryController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("process-definition/{processDefinitionId}/snapshot")
    public KKFProcess getSnapshot(String processDefinitionId) {
        return statsService.buildProcessSnapshot(processDefinitionId);
    }

}
