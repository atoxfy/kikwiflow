package io.kikwiflow.execution;

import io.kikwiflow.model.execution.api.JavaDelegate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestDelegateResolver implements DelegateResolver {
    private final Map<String, JavaDelegate> delegatesMap = new HashMap<>();


    public void register(String name, JavaDelegate delegate){
        delegatesMap.put(name, delegate);
    }

    @Override
    public Optional<JavaDelegate> resolve(String beanName) {
        return Optional.ofNullable(delegatesMap.get(beanName));
    }
}
