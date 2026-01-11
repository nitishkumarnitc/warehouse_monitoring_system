package com.company.bootstrap;

import com.company.common.model.SensorType;
import com.company.warehouse.udp.UdpSensorListener;

public class SystemLauncher {

    public static void main(String[] args) {

        Thread temperatureListener =
                new Thread(new UdpSensorListener(3344, SensorType.TEMPERATURE));

        Thread humidityListener =
                new Thread(new UdpSensorListener(3355, SensorType.HUMIDITY));

        temperatureListener.start();
        humidityListener.start();

        System.out.println("Warehouse Service started (Kafka enabled)");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
