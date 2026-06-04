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

package io.kikwiflow.management.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kikwiflow.management.dtos.elements.KKFFlowNodeDefinition;
import io.kikwiflow.model.stats.KKFMetrics;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KKFProcessStats(
        @JsonProperty("id") String id,
        @JsonProperty("key") String key,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("sla") String sla,
        @JsonProperty("metrics") KKFMetrics metrics,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("flowNodes") Map<String, KKFFlowNodeDefinition> flowNodes,
        @JsonProperty("defaultStartPoint") String defaultStartPoint,
        @JsonProperty("extensionProperties") Map<String, String> extensionProperties
) {}