package io.kikwiflow.execution.delegate;

import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.api.JavaDelegate;

public class AddVariableDelegate implements JavaDelegate {
    @Override
    public void execute(ExecutionContext execution) {
        System.out.println("AddVariableDelegate =>  Before "  + execution.getVariable("food"));

        execution.setVariable("food", "cheeseburger" );
        System.out.println("AddVariableDelegate => After "  + execution.getVariable("food"));

    }
}
