package com.company.common.model;

import java.time.Instant;

public final class SensorReading {

    private final String sensorId;
    private final double value;
    private final SensorType sensorType;
    private final MeasurementUnit unit;
    private final Instant timestamp;

    public SensorReading(
            String sensorId,
            double value,
            SensorType sensorType,
            MeasurementUnit unit,
            Instant timestamp
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
