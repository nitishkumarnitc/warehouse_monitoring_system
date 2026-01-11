package com.company.common.validation;

import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;

/**
 * Validates sensor readings based on physical constraints.
 */
public class SensorValidator {

    // Temperature ranges (Celsius) - based on industrial sensor specs
    private static final double MIN_TEMPERATURE = -40.0;
    private static final double MAX_TEMPERATURE = 60.0;

    // Humidity ranges (percentage)
    private static final double MIN_HUMIDITY = 0.0;
    private static final double MAX_HUMIDITY = 100.0;

    // NOTE: These are hardcoded for now. Should probably make them configurable
    // per sensor or warehouse in the future.

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            return valid ? "VALID" : "INVALID: " + errorMessage;
        }
    }

    /**
     * Validate a sensor reading.
     */
    public static ValidationResult validate(SensorReading reading) {
        if (reading == null) {
            return ValidationResult.invalid("Reading is null");
        }

        if (reading.getSensorId() == null || reading.getSensorId().isEmpty()) {
            return ValidationResult.invalid("Sensor ID is missing");
        }

        if (reading.getTimestamp() == null) {
            return ValidationResult.invalid("Timestamp is missing");
        }

        double value = reading.getValue();

        // Check for NaN and Infinity
        if (Double.isNaN(value)) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Value is NaN", reading.getSensorId()));
        }

        if (Double.isInfinite(value)) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Value is infinite", reading.getSensorId()));
        }

        // Validate based on sensor type
        SensorType type = reading.getSensorType();
        if (type == null) {
            return ValidationResult.invalid("Sensor type is missing");
        }

        // Using switch here - could use Strategy pattern but seems overkill for 2 types
        switch (type) {
            case TEMPERATURE:
                return validateTemperature(reading, value);
            case HUMIDITY:
                return validateHumidity(reading, value);
            default:
                return ValidationResult.invalid("Unknown sensor type: " + type);
        }
    }

    private static ValidationResult validateTemperature(SensorReading reading, double value) {
        if (value < MIN_TEMPERATURE) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Temperature %.2f°C is below minimum %.2f°C",
                    reading.getSensorId(), value, MIN_TEMPERATURE));
        }

        if (value > MAX_TEMPERATURE) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Temperature %.2f°C exceeds maximum %.2f°C",
                    reading.getSensorId(), value, MAX_TEMPERATURE));
        }

        return ValidationResult.valid();
    }

    private static ValidationResult validateHumidity(SensorReading reading, double value) {
        if (value < MIN_HUMIDITY) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Humidity %.2f%% is below minimum %.2f%%",
                    reading.getSensorId(), value, MIN_HUMIDITY));
        }

        if (value > MAX_HUMIDITY) {
            return ValidationResult.invalid(
                String.format("Sensor %s: Humidity %.2f%% exceeds maximum %.2f%%",
                    reading.getSensorId(), value, MAX_HUMIDITY));
        }

        return ValidationResult.valid();
    }

    // Prevent instantiation
    private SensorValidator() {
        throw new UnsupportedOperationException("Utility class");
    }
}
