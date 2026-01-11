package com.company.warehouse;

import com.company.common.metrics.MetricsRegistry;
import com.company.common.model.SensorType;
import com.company.warehouse.udp.UdpSensorListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarehouseApplication {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseApplication.class);

    public static void main(String[] args) throws Exception {

        // Start Prometheus metrics server
        MetricsRegistry.startMetricsServer(8080);
        logger.info("Metrics endpoint available at http://localhost:8080/metrics");

        // Start UDP listeners
        new Thread(new UdpSensorListener(3344, SensorType.TEMPERATURE)).start();
        new Thread(new UdpSensorListener(3355, SensorType.HUMIDITY)).start();

        logger.info("Warehouse Service started successfully");

        // KEEP JVM ALIVE
        Thread.currentThread().join();
    }
}
