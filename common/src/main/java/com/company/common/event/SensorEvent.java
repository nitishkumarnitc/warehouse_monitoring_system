package com.company.common.event;

import com.company.common.model.SensorReading;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SensorEvent {

    private final SensorReading reading;

    @JsonCreator
    public SensorEvent(@JsonProperty("reading") SensorReading reading) {
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
