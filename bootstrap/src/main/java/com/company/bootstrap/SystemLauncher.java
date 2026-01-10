package com.company.bootstrap;

import com.company.common.bus.EventBus;
import com.company.common.model.SensorType;
import com.company.monitoring.alarm.AlarmService;
import com.company.monitoring.consumer.SensorEventConsumer;
import com.company.monitoring.rules.ThresholdEvaluator;
import com.company.warehouse.udp.UdpSensorListener;

public class SystemLauncher {

    public static void main(String[] args) {

        EventBus eventBus = new EventBus();

        AlarmService alarmService = new AlarmService();
        ThresholdEvaluator evaluator = new ThresholdEvaluator(alarmService);
        SensorEventConsumer consumer = new SensorEventConsumer(evaluator);

        eventBus.subscribe(consumer::consume);

        new Thread(new UdpSensorListener(
                3344,
                SensorType.TEMPERATURE,
                eventBus
        )).start();

        new Thread(new UdpSensorListener(
                3355,
                SensorType.HUMIDITY,
                eventBus
        )).start();

        System.out.println("🚀 Warehouse Monitoring System started");
    }
}
