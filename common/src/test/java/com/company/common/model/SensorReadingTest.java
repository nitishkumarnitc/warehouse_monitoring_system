package com.company.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorReadingTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateSensorReadingWithAllFields() {
        String sensorId = "sensor-123";
        double value = 25.5;
        SensorType type = SensorType.TEMPERATURE;
        MeasurementUnit unit = MeasurementUnit.CELSIUS;
        Instant timestamp = Instant.parse("2024-01-01T10:00:00Z");

        SensorReading reading = new SensorReading(sensorId, value, type, unit, timestamp);

        assertThat(reading.getSensorId()).isEqualTo(sensorId);
        assertThat(reading.getValue()).isEqualTo(value);
        assertThat(reading.getSensorType()).isEqualTo(type);
        assertThat(reading.getUnit()).isEqualTo(unit);
        assertThat(reading.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void shouldHandleNegativeValues() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                -10.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );

        assertThat(reading.getValue()).isEqualTo(-10.5);
    }

    @Test
    void shouldHandleZeroValue() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                0.0,
                SensorType.HUMIDITY,
                MeasurementUnit.PERCENT,
                Instant.now()
        );

        assertThat(reading.getValue()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleVeryLargeValues() {
        double largeValue = 999999.99;
        SensorReading reading = new SensorReading(
                "sensor-123",
                largeValue,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );

        assertThat(reading.getValue()).isEqualTo(largeValue);
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        Instant timestamp = Instant.parse("2024-01-01T10:00:00Z");
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                timestamp
        );

        String json = objectMapper.writeValueAsString(reading);

        assertThat(json).contains("\"sensorId\":\"sensor-123\"");
        assertThat(json).contains("\"value\":25.5");
        assertThat(json).contains("\"sensorType\":\"TEMPERATURE\"");
        assertThat(json).contains("\"unit\":\"CELSIUS\"");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = "{" +
                "\"sensorId\":\"sensor-123\"," +
                "\"value\":25.5," +
                "\"sensorType\":\"TEMPERATURE\"," +
                "\"unit\":\"CELSIUS\"," +
                "\"timestamp\":\"2024-01-01T10:00:00Z\"" +
                "}";

        SensorReading reading = objectMapper.readValue(json, SensorReading.class);

        assertThat(reading.getSensorId()).isEqualTo("sensor-123");
        assertThat(reading.getValue()).isEqualTo(25.5);
        assertThat(reading.getSensorType()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(reading.getUnit()).isEqualTo(MeasurementUnit.CELSIUS);
        assertThat(reading.getTimestamp()).isEqualTo(Instant.parse("2024-01-01T10:00:00Z"));
    }

    @Test
    void shouldRoundTripThroughJson() throws Exception {
        Instant timestamp = Instant.parse("2024-01-01T10:00:00Z");
        SensorReading original = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                timestamp
        );

        String json = objectMapper.writeValueAsString(original);
        SensorReading deserialized = objectMapper.readValue(json, SensorReading.class);

        assertThat(deserialized.getSensorId()).isEqualTo(original.getSensorId());
        assertThat(deserialized.getValue()).isEqualTo(original.getValue());
        assertThat(deserialized.getSensorType()).isEqualTo(original.getSensorType());
        assertThat(deserialized.getUnit()).isEqualTo(original.getUnit());
        assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
    }

    @Test
    void shouldIncludeSensorTypeInToString() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );

        String str = reading.toString();

        assertThat(str).contains("sensor-123");
        assertThat(str).contains("25.5");
        assertThat(str).contains("TEMPERATURE");
        assertThat(str).contains(MeasurementUnit.CELSIUS.getSymbol());
    }

    @Test
    void shouldWorkWithHumidityType() {
        SensorReading reading = new SensorReading(
                "sensor-456",
                65.0,
                SensorType.HUMIDITY,
                MeasurementUnit.PERCENT,
                Instant.now()
        );

        assertThat(reading.getSensorType()).isEqualTo(SensorType.HUMIDITY);
        assertThat(reading.getUnit()).isEqualTo(MeasurementUnit.PERCENT);
    }
}
