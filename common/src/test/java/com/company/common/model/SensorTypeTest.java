package com.company.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensorTypeTest {

    @Test
    void shouldHaveTemperatureType() {
        SensorType type = SensorType.TEMPERATURE;

        assertThat(type).isNotNull();
        assertThat(type.name()).isEqualTo("TEMPERATURE");
    }

    @Test
    void shouldHaveHumidityType() {
        SensorType type = SensorType.HUMIDITY;

        assertThat(type).isNotNull();
        assertThat(type.name()).isEqualTo("HUMIDITY");
    }

    @Test
    void shouldHaveExactlyTwoValues() {
        SensorType[] values = SensorType.values();

        assertThat(values).hasSize(2);
        assertThat(values).contains(SensorType.TEMPERATURE, SensorType.HUMIDITY);
    }

    @Test
    void shouldParseFromString() {
        SensorType temperature = SensorType.valueOf("TEMPERATURE");
        SensorType humidity = SensorType.valueOf("HUMIDITY");

        assertThat(temperature).isEqualTo(SensorType.TEMPERATURE);
        assertThat(humidity).isEqualTo(SensorType.HUMIDITY);
    }

    @Test
    void shouldBeComparable() {
        assertThat(SensorType.TEMPERATURE).isNotEqualTo(SensorType.HUMIDITY);
    }
}
