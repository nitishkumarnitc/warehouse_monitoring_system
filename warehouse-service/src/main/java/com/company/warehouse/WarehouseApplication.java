package com.company.warehouse;

import com.company.common.model.SensorType;
import com.company.warehouse.udp.UdpSensorListener;

public class WarehouseApplication {

    public static void main(String[] args) throws Exception {

        new Thread(new UdpSensorListener(3344, SensorType.TEMPERATURE)).start();
        new Thread(new UdpSensorListener(3355, SensorType.HUMIDITY)).start();

        System.out.println("🏭 Warehouse Service started");

        // 🔴 KEEP JVM ALIVE
        Thread.currentThread().join();
    }
}
