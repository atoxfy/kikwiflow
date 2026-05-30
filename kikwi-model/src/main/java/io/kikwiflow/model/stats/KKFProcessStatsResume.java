package io.kikwiflow.model.stats;

import java.time.Instant;

public record KKFProcessStatsResume(String id, String key, String name, String serviceName, Integer version, Instant lastUpdated, KKFMetrics metrics, KKFThresholds thresholds) {

}
