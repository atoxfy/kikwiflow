package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.exception.BadDefinitionExecutionException;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.api.JavaDelegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskExecutorTest {

    private final DelegateResolver delegateResolver;
    private final  TaskExecutor taskExecutor;

    public TaskExecutorTest(){
        this.delegateResolver = mock(DelegateResolver.class);
        this.taskExecutor = new TaskExecutor(delegateResolver);
    }

    @Test
    @DisplayName("")
    void shouldExecute(){
        JavaDelegate delegate = mock(JavaDelegate.class);
        String test = "testdelegate";

        when(delegateResolver.resolve(test)).thenReturn(Optional.ofNullable(delegate));
        ServiceTaskDefinition serviceTaskDefinition = ServiceTaskDefinition.builder()
                .id(UUID.randomUUID().toString())
                .delegateExpression("${" + test + "}")
                .build();

        ExecutionContext context =  new DefaultExecutionContext(null, null, serviceTaskDefinition);

        taskExecutor.execute(context);

    }

    @Test
    @DisplayName("Deve lançar BadDefinitionExecutionException quando o JavaDelegate não é encontrado ")
    void shouldExecuteDelegateResolverIsNull(){
        JavaDelegate delegate = mock(JavaDelegate.class);
        String test = "testdelegate";

        when(delegateResolver.resolve(null)).thenReturn(Optional.ofNullable(delegate));

        ServiceTaskDefinition serviceTaskDefinition = ServiceTaskDefinition.builder()
                .id(UUID.randomUUID().toString())
                .delegateExpression("${" + test + "}")
                .build();

        ExecutionContext context =  new DefaultExecutionContext(null, null, serviceTaskDefinition);

        BadDefinitionExecutionException exception =
                   assertThrows(BadDefinitionExecutionException.class,
                   () -> taskExecutor.execute(context));

        assertEquals("JavaDelegate not found with name: " + test, exception.getMessage());

    }

    @Test
    @DisplayName("")
    void shouldExecuteExecutableTaskNotFlowDefinition(){
        JavaDelegate delegate = mock(JavaDelegate.class);
        String test = "testdelegate";

        ServiceTaskDefinition serviceTaskDefinition = ServiceTaskDefinition.builder()
                .id(UUID.randomUUID().toString())
                .build();


        ExecutionContext context =  new DefaultExecutionContext(null, null, serviceTaskDefinition);

        BadDefinitionExecutionException exception =
                assertThrows(BadDefinitionExecutionException.class,
                        () -> taskExecutor.execute(context));

        assertEquals("Invalid execution method for task " + serviceTaskDefinition.id(), exception.getMessage());

    }

}