package com.company.warehouse;

import com.company.common.bus.EventBus;
import com.company.common.model.SensorType;
import com.company.warehouse.udp.UdpSensorListener;

public class WarehouseApplication {

    public static void main(String[] args) {

        EventBus eventBus = new EventBus();

        UdpSensorListener temperatureListener =
                new UdpSensorListener(
                        3344,
                        SensorType.TEMPERATURE,
                        eventBus
                );

        UdpSensorListener humidityListener =
                new UdpSensorListener(
                        3355,
                        SensorType.HUMIDITY,
                        eventBus
                );

        new Thread(temperatureListener).start();
        new Thread(humidityListener).start();

        System.out.println("Warehouse Service started");
    }
}
