package com.company.monitoring;

import com.company.common.metrics.MetricsRegistry;
import com.company.monitoring.kafka.SensorEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CentralMonitoringApplication {

    private static final Logger logger = LoggerFactory.getLogger(CentralMonitoringApplication.class);

    public static void main(String[] args) throws Exception {

        // Start Prometheus metrics server
        MetricsRegistry.startMetricsServer(8081);
        logger.info("Metrics endpoint available at http://localhost:8081/metrics");

        // Start Kafka consumers
        new Thread(new SensorEventConsumer("sensor-events")).start();
        new Thread(new SensorEventConsumer("sensor-events-retry-1")).start();
        new Thread(new SensorEventConsumer("sensor-events-retry-2")).start();

        logger.info("Central Monitoring Service started successfully");

        Thread.currentThread().join();
    }
}
