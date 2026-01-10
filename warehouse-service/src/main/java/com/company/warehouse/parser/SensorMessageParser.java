package com.company.warehouse.parser;

import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;

import java.time.Instant;
import java.util.Map;

public class SensorMessageParser {

    public SensorReading parse(
            String message,
            SensorType sensorType,
            MeasurementUnit unit
    ) {
        // Example: sensor_id=t1; value=30
        Map<String, String> parts = parseKeyValue(message);

        String sensorId = parts.get("sensor_id");
        double value = Double.parseDouble(parts.get("value"));

        return new SensorReading(
                sensorId,
                value,
                sensorType,
                unit,
                Instant.now()
        );
    }

    private Map<String, String> parseKeyValue(String message) {
        return message.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split(";")))
                .map(String::trim)
                .map(kv -> kv.split("="))
                .collect(java.util.stream.Collectors.toMap(
                        kv -> kv[0].trim(),
                        kv -> kv[1].trim()
                ));
    }
}
