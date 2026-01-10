package com.company.warehouse.publisher;

import com.company.common.event.SensorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSensorEventPublisher implements SensorEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(LoggingSensorEventPublisher.class);

    @Override
    public void publish(SensorEvent event) {
        log.info("Publishing sensor event: {}", event);
    }
}
