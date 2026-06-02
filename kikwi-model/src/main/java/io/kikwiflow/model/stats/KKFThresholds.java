package io.kikwiflow.model.stats;

public record KKFThresholds(
        Double slaWarning,
        Double slaCritical,
        Long failCritical) {
}
