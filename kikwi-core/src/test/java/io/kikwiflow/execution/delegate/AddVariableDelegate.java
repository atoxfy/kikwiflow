package io.kikwiflow.execution.delegate;

import io.kikwiflow.model.execution.ExecutionContext;
import io.kikwiflow.model.execution.JavaDelegate;

public class AddVariableDelegate implements JavaDelegate {
    @Override
    public void execute(ExecutionContext execution) {
        System.out.println("AddVariableDelegate =>  Before "  + execution.getVariable("food"));

        execution.setVariable("food", "cheeseburger" );
        System.out.println("AddVariableDelegate => After "  + execution.getVariable("food"));

    }
}
