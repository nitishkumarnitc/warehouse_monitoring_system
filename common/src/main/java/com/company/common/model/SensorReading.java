package com.company.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public final class SensorReading {

    private final String sensorId;
    private final double value;
    private final SensorType sensorType;
    private final MeasurementUnit unit;
    private final Instant timestamp;

    @JsonCreator
    public SensorReading(
            @JsonProperty("sensorId") String sensorId,
            @JsonProperty("value") double value,
            @JsonProperty("sensorType") SensorType sensorType,
            @JsonProperty("unit") MeasurementUnit unit,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        this.sensorId = sensorId;
        this.value = value;
        this.sensorType = sensorType;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    public String getSensorId() {
        return sensorId;
    }

    public double getValue() {
        return value;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SensorReading{" +
                "sensorId='" + sensorId + '\'' +
                ", value=" + value + unit.getSymbol() +
                ", sensorType=" + sensorType +
                ", timestamp=" + timestamp +
                '}';
    }
}
