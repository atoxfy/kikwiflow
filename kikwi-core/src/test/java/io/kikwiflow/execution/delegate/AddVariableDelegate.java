package io.kikwiflow.execution.delegate;

import io.kikwiflow.api.ExecutionContext;
import io.kikwiflow.api.JavaDelegate;

public class AddVariableDelegate implements JavaDelegate {
    @Override
    public void execute(ExecutionContext execution) {
        execution.setVariable("food", "cheeseburger" );
    }
}
