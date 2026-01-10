package com.company.monitoring;

import com.company.common.event.SensorEvent;
import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import com.company.monitoring.alarm.AlarmService;
import com.company.monitoring.consumer.SensorEventConsumer;
import com.company.monitoring.rules.ThresholdEvaluator;

import java.time.Instant;

public class MonitoringApplication {

    public static void main(String[] args) {

        AlarmService alarmService = new AlarmService();
        ThresholdEvaluator evaluator = new ThresholdEvaluator(alarmService);
        SensorEventConsumer consumer = new SensorEventConsumer(evaluator);

        // 🔹 Temporary simulation (until broker wiring)
        SensorEvent tempEvent = new SensorEvent(
                new SensorReading(
                        "t1",
                        40.0,
                        SensorType.TEMPERATURE,
                        MeasurementUnit.CELSIUS,
                        Instant.now()
                )
        );

        consumer.consume(tempEvent);
    }
}
