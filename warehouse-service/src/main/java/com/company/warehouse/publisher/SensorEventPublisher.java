package com.company.warehouse.publisher;

import com.company.common.event.SensorEvent;

public interface SensorEventPublisher {
    void publish(SensorEvent event);
}
