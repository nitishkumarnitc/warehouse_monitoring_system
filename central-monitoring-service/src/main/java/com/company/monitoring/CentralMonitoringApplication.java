package com.company.monitoring;

import com.company.common.config.ApplicationConfig;
import com.company.common.config.ServiceConfig;
import com.company.common.kafka.RetryPublisher;
import com.company.common.metrics.MetricsRegistry;
import com.company.common.resilience.GracefulShutdown;
import com.company.monitoring.kafka.KafkaConsumerFactory;
import com.company.monitoring.kafka.SensorEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CentralMonitoringApplication {

    private static final Logger logger = LoggerFactory.getLogger(CentralMonitoringApplication.class);

    public static void main(String[] args) throws Exception {

        // Initialize and validate configuration
        ServiceConfig config = new ServiceConfig(new ApplicationConfig());
        config.validate();
        logger.info("Configuration validated: Kafka bootstrap servers = {}", config.getKafkaBootstrapServers());

        // Initialize graceful shutdown manager
        GracefulShutdown shutdown = new GracefulShutdown();

        // Start Prometheus metrics server
        int metricsPort = config.getPrometheusPortMonitoring();
        MetricsRegistry.startMetricsServer(metricsPort);
        logger.info("Metrics endpoint available at http://localhost:{}/metrics", metricsPort);

        // Register metrics server shutdown
        shutdown.registerRunnable("metrics-server", MetricsRegistry::stopMetricsServer);

        // Create RetryPublisher for failed message handling
        RetryPublisher retryPublisher = new RetryPublisher(config);
        logger.info("RetryPublisher initialized");

        // Register RetryPublisher shutdown
        shutdown.registerRunnable("retry-publisher", retryPublisher::close);

        // Start Kafka consumers with proper dependency injection
        Thread consumer1 = new Thread(new SensorEventConsumer(
            config.getKafkaMainTopic(),
            KafkaConsumerFactory.create(),
            retryPublisher
        ));
        consumer1.setName("consumer-main-topic");
        consumer1.start();

        Thread consumer2 = new Thread(new SensorEventConsumer(
            config.getKafkaRetryTopic1(),
            KafkaConsumerFactory.create(),
            retryPublisher
        ));
        consumer2.setName("consumer-retry-1");
        consumer2.start();

        Thread consumer3 = new Thread(new SensorEventConsumer(
            config.getKafkaRetryTopic2(),
            KafkaConsumerFactory.create(),
            retryPublisher
        ));
        consumer3.setName("consumer-retry-2");
        consumer3.start();

        logger.info("Central Monitoring Service started successfully");
        logger.info("  - Main topic consumer: {}", config.getKafkaMainTopic());
        logger.info("  - Retry-1 topic consumer: {}", config.getKafkaRetryTopic1());
        logger.info("  - Retry-2 topic consumer: {}", config.getKafkaRetryTopic2());

        Thread.currentThread().join();
    }
}
