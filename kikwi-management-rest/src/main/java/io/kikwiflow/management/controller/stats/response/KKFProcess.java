package io.kikwiflow.management.controller.stats.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kikwiflow.management.controller.stats.response.elements.KKFFlowNodeDefinition;
import io.kikwiflow.model.stats.KKFMetrics;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KKFProcess(
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