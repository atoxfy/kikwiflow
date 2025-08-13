package io.kikwiflow.event;

import java.util.List;

public interface EventPublisher {

    public void registerListener(ExecutionEventListener listener);

    public void removeListener(ExecutionEventListener listener);

    public void publishEvent(LightweightEvent event);

    public void publishEvents(List<LightweightEvent> events);
}
