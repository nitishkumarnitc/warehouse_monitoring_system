package com.company.common.event;

import com.company.common.model.SensorReading;

public final class SensorEvent {

    private final SensorReading reading;

    public SensorEvent(SensorReading reading) {
        this.reading = reading;
    }

    public SensorReading getReading() {
        return reading;
    }

    @Override
    public String toString() {
        return "SensorEvent{" + reading + '}';
    }
}
