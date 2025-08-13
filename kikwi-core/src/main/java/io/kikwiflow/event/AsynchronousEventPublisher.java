package io.kikwiflow.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

public class AsynchronousEventPublisher implements EventPublisher {

    private final List<ExecutionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService listenerExecutor;

    public AsynchronousEventPublisher(ExecutorService executorService){
        this.listenerExecutor = executorService;
    }

    @Override
    public void registerListener(ExecutionEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ExecutionEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void publishEvent(LightweightEvent event) {
        publishEvents(List.of(event));
    }

    @Override
    public void publishEvents(List<LightweightEvent> events){
        for (ExecutionEventListener listener : listeners) {
            listenerExecutor.submit(() -> {
                listener.onEvents(events);
            });
        }
    }
}
