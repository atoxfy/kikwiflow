package io.kikwiflow.view.model.manual;

import java.util.List;

public record Workflow(String id, String key, String name, String description, List<WorkflowStage> stages) {

}
