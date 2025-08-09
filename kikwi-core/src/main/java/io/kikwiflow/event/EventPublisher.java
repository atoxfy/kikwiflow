package io.kikwiflow.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventPublisher {

    private final List<ExecutionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService listenerExecutor;

    public EventPublisher(ExecutorService executorService){
        this.listenerExecutor = executorService;
    }

    public void registerListener(ExecutionEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ExecutionEventListener listener) {
        listeners.remove(listener);
    }

    public void publishEvent(ExecutionEvent event) {
        publishEvents(List.of(event));
    }

    public void publishEvents(List<ExecutionEvent> events){
        for (ExecutionEventListener listener : listeners) {
            listenerExecutor.submit(() -> {
                listener.onEvents(events);
            });
        }
    }
}
