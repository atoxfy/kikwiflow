package io.kikwiflow.exception;

public class ProcessInstanceNotFoundException extends RuntimeException {

    private static final String MESSAGE = "Process instance not found with id: %s";

    public ProcessInstanceNotFoundException(String processInstanceId) {
        super(String.format(MESSAGE, processInstanceId));
    }
}