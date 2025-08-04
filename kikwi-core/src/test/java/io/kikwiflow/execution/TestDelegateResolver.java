package io.kikwiflow.execution;

import io.kikwiflow.api.JavaDelegate;
import io.kikwiflow.execution.DelegateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestDelegateResolver implements DelegateResolver {
    private final Map<String, JavaDelegate> delegatesMap = new HashMap<>();


    public void register(String name, JavaDelegate delegate){
        delegatesMap.put(name, delegate);
    }

    @Override
    public JavaDelegate resolve(String beanName) {
        return null;
    }
}
