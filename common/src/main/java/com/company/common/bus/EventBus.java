package com.company.common.bus;

import com.company.common.event.SensorEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventBus {

    private final List<Consumer<SensorEvent>> subscribers = new ArrayList<>();

    public void subscribe(Consumer<SensorEvent> consumer) {
        subscribers.add(consumer);
    }

    public void publish(SensorEvent event) {
        for (Consumer<SensorEvent> consumer : subscribers) {
            consumer.accept(event);
        }
    }
}
