package com.company.warehouse;

import com.company.common.config.ServiceConfig;
import com.company.common.health.HealthCheckServer;
import com.company.common.metrics.MetricsRegistry;
import com.company.common.model.SensorType;
import com.company.common.resilience.GracefulShutdown;
import com.company.common.resilience.ResilientKafkaProducer;
import com.company.warehouse.kafka.KafkaProducerFactory;
import com.company.warehouse.udp.UdpSensorListener;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Warehouse Service main application.
 * Receives sensor data via UDP and publishes to Kafka.
 */
public class WarehouseApplication {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseApplication.class);

    public static void main(String[] args) {
        logger.info("Starting Warehouse Service...");

        // Load and validate configuration
        ServiceConfig config = new ServiceConfig();
        config.validate();
        logger.info("Configuration validated successfully");

        // Initialize graceful shutdown manager
        GracefulShutdown shutdown = new GracefulShutdown();

        try {
            // Start Prometheus metrics server
            int metricsPort = config.getPrometheusPortWarehouse();
            MetricsRegistry.startMetricsServer(metricsPort);
            logger.info("Metrics endpoint: http://localhost:{}/metrics", metricsPort);

            // Register metrics server shutdown
            shutdown.registerRunnable("metrics-server", MetricsRegistry::stopMetricsServer);

            // Create Kafka producer with circuit breaker
            KafkaProducer<String, String> kafkaProducer = KafkaProducerFactory.create(config);
            ResilientKafkaProducer<String, String> producer =
                new ResilientKafkaProducer<>(kafkaProducer, "warehouse-producer");

            // Register producer shutdown
            shutdown.registerTask("kafka-producer", new GracefulShutdown.ShutdownTask() {
                @Override
                public void execute() throws Exception {
                    logger.info("Flushing Kafka producer...");
                    producer.flush();
                    producer.close();
                }

                @Override
                public String getName() {
                    return "kafka-producer";
                }
            });

            // Start health check server
            int healthPort = config.getHealthPortWarehouse();
            HealthCheckServer healthServer = new HealthCheckServer(healthPort);

            // Register health indicators
            healthServer.registerHealthIndicator("kafka-circuit-breaker", new HealthCheckServer.HealthIndicator() {
                @Override
                public boolean isHealthy() {
                    return producer.getCircuitBreakerState().toString().equals("CLOSED");
                }

                @Override
                public String getStatus() {
                    return producer.getCircuitBreakerState().toString();
                }
            });

            healthServer.start();
            logger.info("Health check endpoint: http://localhost:{}/health", healthPort);
            logger.info("Readiness endpoint: http://localhost:{}/ready", healthPort);

            // Register health server shutdown
            shutdown.registerCloseable("health-server", healthServer::stop);

            // Create and start UDP listeners
            // TODO: refactor this into a factory pattern when we add more sensor types
            List<UdpSensorListener> listeners = new ArrayList<>();

            int tempPort = config.getTemperaturePort();
            UdpSensorListener tempListener = new UdpSensorListener(
                tempPort, SensorType.TEMPERATURE, producer, config
            );
            Thread tempThread = new Thread(tempListener, "udp-temperature-" + tempPort);
            tempThread.start();
            listeners.add(tempListener);

            // Same pattern for humidity - could definitely DRY this up
            int humidityPort = config.getHumidityPort();
            UdpSensorListener humidityListener = new UdpSensorListener(
                humidityPort, SensorType.HUMIDITY, producer, config
            );
            Thread humidityThread = new Thread(humidityListener, "udp-humidity-" + humidityPort);
            humidityThread.start();
            listeners.add(humidityListener);

            // Register listener shutdown
            shutdown.registerTask("udp-listeners", new GracefulShutdown.ShutdownTask() {
                @Override
                public void execute() {
                    for (UdpSensorListener listener : listeners) {
                        listener.stop();
                    }
                }

                @Override
                public String getName() {
                    return "udp-listeners";
                }
            });

            // Mark service as ready
            healthServer.setReady(true);

            logger.info("Warehouse Service started successfully");
            logger.info("  - Temperature port: {}", tempPort);
            logger.info("  - Humidity port: {}", humidityPort);
            logger.info("  - Kafka topic: {}", config.getKafkaMainTopic());
            logger.info("  - Rate limit: {}/s per port", config.getUdpRateLimit());

            // Keep JVM alive
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start Warehouse Service", e);
            shutdown.shutdown();
            System.exit(1);
        }
    }
}
