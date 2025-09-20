package io.kikwiflow.view.model.manual;

import java.util.List;
import java.util.Map;

public record WorkflowStage(String id, String name, List<String> outgoing, Map<String, String> additionalProperties) {

}
