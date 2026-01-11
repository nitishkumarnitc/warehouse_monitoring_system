package com.company.common.validation;

import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class SensorValidatorTest {

    @Test
    void shouldAcceptValidTemperature() {
        SensorReading reading = new SensorReading(
            "sensor-1", 25.5, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptValidHumidity() {
        SensorReading reading = new SensorReading(
            "sensor-2", 65.0, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectNaN() {
        SensorReading reading = new SensorReading(
            "sensor-1", Double.NaN, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("NaN");
    }

    @Test
    void shouldRejectInfinity() {
        SensorReading reading = new SensorReading(
            "sensor-1", Double.POSITIVE_INFINITY, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("infinite");
    }

    @Test
    void shouldRejectNegativeInfinity() {
        SensorReading reading = new SensorReading(
            "sensor-1", Double.NEGATIVE_INFINITY, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("infinite");
    }

    @Test
    void shouldRejectTemperatureTooLow() {
        SensorReading reading = new SensorReading(
            "sensor-1", -50.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("below minimum");
    }

    @Test
    void shouldRejectTemperatureTooHigh() {
        SensorReading reading = new SensorReading(
            "sensor-1", 70.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("exceeds maximum");
    }

    @Test
    void shouldAcceptBoundaryTemperatures() {
        SensorReading minTemp = new SensorReading(
            "sensor-1", -40.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );
        assertThat(SensorValidator.validate(minTemp).isValid()).isTrue();

        SensorReading maxTemp = new SensorReading(
            "sensor-1", 60.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );
        assertThat(SensorValidator.validate(maxTemp).isValid()).isTrue();
    }

    @Test
    void shouldRejectHumidityTooLow() {
        SensorReading reading = new SensorReading(
            "sensor-2", -5.0, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("below minimum");
    }

    @Test
    void shouldRejectHumidityTooHigh() {
        SensorReading reading = new SensorReading(
            "sensor-2", 105.0, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("exceeds maximum");
    }

    @Test
    void shouldAcceptBoundaryHumidity() {
        SensorReading minHumidity = new SensorReading(
            "sensor-2", 0.0, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );
        assertThat(SensorValidator.validate(minHumidity).isValid()).isTrue();

        SensorReading maxHumidity = new SensorReading(
            "sensor-2", 100.0, SensorType.HUMIDITY, MeasurementUnit.PERCENT, Instant.now()
        );
        assertThat(SensorValidator.validate(maxHumidity).isValid()).isTrue();
    }

    @Test
    void shouldRejectNullReading() {
        SensorValidator.ValidationResult result = SensorValidator.validate(null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null");
    }

    @Test
    void shouldRejectNullSensorId() {
        SensorReading reading = new SensorReading(
            null, 25.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Sensor ID");
    }

    @Test
    void shouldRejectEmptySensorId() {
        SensorReading reading = new SensorReading(
            "", 25.0, SensorType.TEMPERATURE, MeasurementUnit.CELSIUS, Instant.now()
        );

        SensorValidator.ValidationResult result = SensorValidator.validate(reading);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Sensor ID");
    }
}
