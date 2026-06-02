package io.kikwiflow.management.controller.stats.mapper;

import io.kikwiflow.model.definition.process.ProcessDefinition;
import io.kikwiflow.model.stats.KKFMetrics;
import io.kikwiflow.model.stats.KKFProcessStatsResume;
import io.kikwiflow.model.stats.KKFThresholds;

import java.time.Instant;

public class DashboardMapper {
    public static KKFProcessStatsResume fromProcessDefinition(ProcessDefinition processDefinition, Long quantity){
        return new KKFProcessStatsResume(
                processDefinition.id(),
                processDefinition.key(),
                processDefinition.name(),
                "sales-ms",
                processDefinition.version(),
                Instant.now(),
                new KKFMetrics(quantity, 100.00, 0L),
                new KKFThresholds(95.0, 80.0, 1L)
        );
    }
}
