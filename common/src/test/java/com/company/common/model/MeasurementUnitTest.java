package com.company.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasurementUnitTest {

    @Test
    void shouldHaveCelsiusUnit() {
        MeasurementUnit unit = MeasurementUnit.CELSIUS;

        assertThat(unit).isNotNull();
        assertThat(unit.name()).isEqualTo("CELSIUS");
    }

    @Test
    void shouldHavePercentUnit() {
        MeasurementUnit unit = MeasurementUnit.PERCENT;

        assertThat(unit).isNotNull();
        assertThat(unit.name()).isEqualTo("PERCENT");
    }

    @Test
    void shouldReturnCelsiusSymbol() {
        String symbol = MeasurementUnit.CELSIUS.getSymbol();

        assertThat(symbol).isEqualTo("°C");
    }

    @Test
    void shouldReturnPercentSymbol() {
        String symbol = MeasurementUnit.PERCENT.getSymbol();

        assertThat(symbol).isEqualTo("%");
    }

    @Test
    void shouldHaveExactlyTwoValues() {
        MeasurementUnit[] values = MeasurementUnit.values();

        assertThat(values).hasSize(2);
        assertThat(values).contains(MeasurementUnit.CELSIUS, MeasurementUnit.PERCENT);
    }

    @Test
    void shouldParseFromString() {
        MeasurementUnit celsius = MeasurementUnit.valueOf("CELSIUS");
        MeasurementUnit percent = MeasurementUnit.valueOf("PERCENT");

        assertThat(celsius).isEqualTo(MeasurementUnit.CELSIUS);
        assertThat(percent).isEqualTo(MeasurementUnit.PERCENT);
    }

    @Test
    void shouldBeComparable() {
        assertThat(MeasurementUnit.CELSIUS).isNotEqualTo(MeasurementUnit.PERCENT);
    }
}
