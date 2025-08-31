package io.kikwiflow.execution;

import io.kikwiflow.api.DefaultExecutionContext;
import io.kikwiflow.exception.BadDefinitionExecutionException;
import io.kikwiflow.model.bpmn.elements.ServiceTaskDefinition;
import io.kikwiflow.model.bpmn.elements.StartEventDefinition;
import io.kikwiflow.model.execution.api.ExecutionContext;
import io.kikwiflow.model.execution.api.JavaDelegate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        String beanName = "testdelegate";

        when(delegateResolver.resolve(beanName)).thenReturn(Optional.ofNullable(delegate));
        ServiceTaskDefinition serviceTaskDefinition = ServiceTaskDefinition.builder()
                .id(UUID.randomUUID().toString())
                .delegateExpression("${" + beanName + "}")
                .build();

        ExecutionContext context =  new DefaultExecutionContext(null, null, serviceTaskDefinition);

        taskExecutor.execute(context);

        verify(delegateResolver, times(1)).resolve(argThat(variable -> {
            assertNotNull(variable);
            assertEquals(beanName, variable, "O nome do bean passado para o resolver está incorreto");

            assertNotNull(variable, "O nome do bean não deveria ser nulo");
            assertFalse(variable.contains("${"), "O nome do bean não deveria conter expressão");
            assertFalse(variable.contains("}"), "O nome do bean não deveria conter chaves");


            return true;

        }));

        verify(delegate, times(1)).execute(argThat(variable -> {
            assertNotNull(variable, "O ExecutionContext não deveria ser nulo");
            assertTrue(variable.getFlowNode() instanceof ServiceTaskDefinition, "O FlowNode deveria ser um ServiceTaskDefinition");

            return true;
        }));

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
    @DisplayName("Deve lançar BadDefinitionExecutionException quando o serviceTask for Null")
    void shouldExecuteExecutableTaskNotFlowDefinition(){
        ServiceTaskDefinition serviceTaskDefinition = ServiceTaskDefinition.builder()
                .id(UUID.randomUUID().toString())
                .build();


        ExecutionContext context =  new DefaultExecutionContext(null, null, serviceTaskDefinition);

        BadDefinitionExecutionException exception =
                assertThrows(BadDefinitionExecutionException.class,
                        () -> taskExecutor.execute(context));

        assertEquals("Invalid execution method for task " + serviceTaskDefinition.id(), exception.getMessage());

    }

    @Test
    @DisplayName("")
    void shouldNotEnterIfWhenFlowNodeIsNotServiceTaskDefinition() {
        JavaDelegate delegate = mock(JavaDelegate.class);

        ExecutionContext context = new DefaultExecutionContext(null, null, null);

        taskExecutor.execute(context);

        verifyNoInteractions(delegateResolver);
        verifyNoInteractions(delegate);

    }

}