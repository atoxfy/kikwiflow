package io.kikwiflow.execution.delegate;

import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.execution.JavaDelegate;

public class RemoveVariableDelegate implements JavaDelegate {
    @Override
    public void execute(ExecutionContext execution) {
        System.out.println("RemoveVariableDelegate => Before "  + execution.getVariable("food"));
        execution.removeVariable("food");
        System.out.println("RemoveVariableDelegate => After "  + execution.getVariable("food"));
    }
}
