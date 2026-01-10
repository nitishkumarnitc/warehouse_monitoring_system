package com.company.monitoring;


import com.company.monitoring.kafka.SensorEventConsumer;

public class CentralMonitoringApplication {

    public static void main(String[] args) {

        new Thread(new SensorEventConsumer("sensor-events")).start();
        new Thread(new SensorEventConsumer("sensor-events-retry-1")).start();
        new Thread(new SensorEventConsumer("sensor-events-retry-2")).start();

        System.out.println("📡 Central Monitoring Service started");
    }
}
