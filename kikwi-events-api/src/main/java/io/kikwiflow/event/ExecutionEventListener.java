package io.kikwiflow.event;

import java.util.List;

@FunctionalInterface
public interface ExecutionEventListener {
    void onEvents(List<ExecutionEvent> events);

}
