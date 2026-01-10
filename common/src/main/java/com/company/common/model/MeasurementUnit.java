package com.company.common.model;

public enum MeasurementUnit {
    CELSIUS("°C"),
    PERCENT("%");

    private final String symbol;

    MeasurementUnit(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
