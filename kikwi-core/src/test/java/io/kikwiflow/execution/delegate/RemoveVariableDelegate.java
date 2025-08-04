package io.kikwiflow.execution.delegate;

import io.kikwiflow.api.ExecutionContext;
import io.kikwiflow.api.JavaDelegate;

public class RemoveVariableDelegate implements JavaDelegate {
    @Override
    public void execute(ExecutionContext execution) {
        execution.removeVariable("food");
    }
}
