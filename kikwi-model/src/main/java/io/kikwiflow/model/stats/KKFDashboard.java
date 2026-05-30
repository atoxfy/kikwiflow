package io.kikwiflow.model.stats;

import java.util.List;

public record KKFDashboard (
        Long total,
        List<KKFProcessStatsResume> processes
){

}
