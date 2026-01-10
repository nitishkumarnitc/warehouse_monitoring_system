package com.company.monitoring.rules;

import com.company.common.event.SensorEvent;
import com.company.common.model.SensorType;
import com.company.monitoring.alarm.AlarmService;

public class ThresholdEvaluator {

    private static final double TEMP_THRESHOLD = 35.0;
    private static final double HUMIDITY_THRESHOLD = 50.0;

    private final AlarmService alarmService;

    public ThresholdEvaluator(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    public void evaluate(SensorEvent event) {
        var reading = event.getReading();

        if (reading.getSensorType() == SensorType.TEMPERATURE &&
                reading.getValue() > TEMP_THRESHOLD) {

            alarmService.raiseAlarm(
                    "Temperature threshold exceeded: " + reading.getValue() + "°C",
                    event
            );
        }

        if (reading.getSensorType() == SensorType.HUMIDITY &&
                reading.getValue() > HUMIDITY_THRESHOLD) {

            alarmService.raiseAlarm(
                    "Humidity threshold exceeded: " + reading.getValue() + "%",
                    event
            );
        }
    }
}
