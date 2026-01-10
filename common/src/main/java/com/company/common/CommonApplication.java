package com.company.common;

import com.company.common.bus.EventBus;
import com.company.common.event.SensorEvent;
import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;

import java.time.Instant;

public class CommonApplication {

    public static void main(String[] args) {

        EventBus eventBus = new EventBus();

        // Simple subscriber for verification
        eventBus.subscribe(event ->
                System.out.println("Received event in common: " + event)
        );

        SensorReading reading = new SensorReading(
                "t1",
                42.0,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );

        SensorEvent event = new SensorEvent(reading);
        eventBus.publish(event);
    }
}
