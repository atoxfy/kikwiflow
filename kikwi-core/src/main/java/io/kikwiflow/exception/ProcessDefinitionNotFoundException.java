package io.kikwiflow.exception;

public class ProcessDefinitionNotFoundException extends RuntimeException{
    public ProcessDefinitionNotFoundException(String message) {
        super(message);
    }
}
