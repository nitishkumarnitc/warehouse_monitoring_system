package com.company.warehouse.parser;

import com.company.common.model.*;

import java.time.Instant;

public class SensorMessageParser {

    public static SensorReading parse(String message, SensorType type) {
        // Example: sensor_id=t1; value=30
        String[] parts = message.split(";");

        String sensorId = parts[0].split("=")[1].trim();
        double value = Double.parseDouble(parts[1].split("=")[1].trim());

        MeasurementUnit unit =
                type == SensorType.TEMPERATURE ? MeasurementUnit.CELSIUS : MeasurementUnit.PERCENT;

        return new SensorReading(
                sensorId,
                value,
                type,
                unit,
                Instant.now()
        );
    }
}
