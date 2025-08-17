/*
 * Copyright 2025 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
