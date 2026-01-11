package com.company.common.event;

import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateSensorEventWithReading() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );

        SensorEvent event = new SensorEvent(reading);

        assertThat(event.getReading()).isEqualTo(reading);
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.parse("2024-01-01T10:00:00Z")
        );
        SensorEvent event = new SensorEvent(reading);

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"reading\"");
        assertThat(json).contains("\"sensorId\":\"sensor-123\"");
        assertThat(json).contains("\"value\":25.5");
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        String json = "{\"reading\":{" +
                "\"sensorId\":\"sensor-123\"," +
                "\"value\":25.5," +
                "\"sensorType\":\"TEMPERATURE\"," +
                "\"unit\":\"CELSIUS\"," +
                "\"timestamp\":\"2024-01-01T10:00:00Z\"" +
                "}}";

        SensorEvent event = objectMapper.readValue(json, SensorEvent.class);

        assertThat(event.getReading()).isNotNull();
        assertThat(event.getReading().getSensorId()).isEqualTo("sensor-123");
        assertThat(event.getReading().getValue()).isEqualTo(25.5);
    }

    @Test
    void shouldRoundTripThroughJson() throws Exception {
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.parse("2024-01-01T10:00:00Z")
        );
        SensorEvent original = new SensorEvent(reading);

        String json = objectMapper.writeValueAsString(original);
        SensorEvent deserialized = objectMapper.readValue(json, SensorEvent.class);

        assertThat(deserialized.getReading().getSensorId())
                .isEqualTo(original.getReading().getSensorId());
        assertThat(deserialized.getReading().getValue())
                .isEqualTo(original.getReading().getValue());
        assertThat(deserialized.getReading().getSensorType())
                .isEqualTo(original.getReading().getSensorType());
    }

    @Test
    void shouldIncludeReadingInToString() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                25.5,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );
        SensorEvent event = new SensorEvent(reading);

        String str = event.toString();

        assertThat(str).contains("SensorEvent");
        assertThat(str).contains("sensor-123");
    }

    @Test
    void shouldWorkWithHumidityReading() {
        SensorReading reading = new SensorReading(
                "sensor-456",
                65.0,
                SensorType.HUMIDITY,
                MeasurementUnit.PERCENT,
                Instant.now()
        );
        SensorEvent event = new SensorEvent(reading);

        assertThat(event.getReading().getSensorType()).isEqualTo(SensorType.HUMIDITY);
        assertThat(event.getReading().getValue()).isEqualTo(65.0);
    }

    @Test
    void shouldHandleNegativeValues() {
        SensorReading reading = new SensorReading(
                "sensor-123",
                -10.0,
                SensorType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                Instant.now()
        );
        SensorEvent event = new SensorEvent(reading);

        assertThat(event.getReading().getValue()).isEqualTo(-10.0);
    }
}
