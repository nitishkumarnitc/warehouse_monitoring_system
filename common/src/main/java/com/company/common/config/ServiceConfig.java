package com.company.common.config;

/**
 * Configuration constants for warehouse monitoring services.
 * All values can be overridden via system properties or environment variables.
 */
public class ServiceConfig {

    private final ApplicationConfig config;

    public ServiceConfig() {
        this.config = new ApplicationConfig();
    }

    public ServiceConfig(ApplicationConfig config) {
        this.config = config;
    }

    // ======== UDP Configuration ========

    public static final class Udp {
        public static final int DEFAULT_TEMPERATURE_PORT = 3344;
        public static final int DEFAULT_HUMIDITY_PORT = 3355;
        public static final int DEFAULT_BUFFER_SIZE = 1024;
        public static final int DEFAULT_RATE_LIMIT_PER_SECOND = 1000;
    }

    public int getTemperaturePort() {
        return config.getInt("udp.temperature.port", Udp.DEFAULT_TEMPERATURE_PORT);
    }

    public int getHumidityPort() {
        return config.getInt("udp.humidity.port", Udp.DEFAULT_HUMIDITY_PORT);
    }

    public int getUdpBufferSize() {
        return config.getInt("udp.buffer.size", Udp.DEFAULT_BUFFER_SIZE);
    }

    public int getUdpRateLimit() {
        return config.getInt("udp.rate.limit", Udp.DEFAULT_RATE_LIMIT_PER_SECOND);
    }

    // ======== Kafka Configuration ========

    public static final class Kafka {
        public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
        public static final String DEFAULT_MAIN_TOPIC = "sensor-events";
        public static final String DEFAULT_RETRY_TOPIC_1 = "sensor-events-retry-1";
        public static final String DEFAULT_RETRY_TOPIC_2 = "sensor-events-retry-2";
        public static final String DEFAULT_DLQ_TOPIC = "sensor-events-dlq";
        public static final String DEFAULT_CONSUMER_GROUP = "central-monitoring-group";
        public static final int DEFAULT_POLL_TIMEOUT_MS = 500;
        public static final int DEFAULT_MAX_RETRIES = 3;
    }

    public String getKafkaBootstrapServers() {
        return config.get("kafka.bootstrap.servers", Kafka.DEFAULT_BOOTSTRAP_SERVERS);
    }

    public String getKafkaMainTopic() {
        return config.get("kafka.topic.main", Kafka.DEFAULT_MAIN_TOPIC);
    }

    public String getKafkaRetryTopic1() {
        return config.get("kafka.topic.retry1", Kafka.DEFAULT_RETRY_TOPIC_1);
    }

    public String getKafkaRetryTopic2() {
        return config.get("kafka.topic.retry2", Kafka.DEFAULT_RETRY_TOPIC_2);
    }

    public String getKafkaDlqTopic() {
        return config.get("kafka.topic.dlq", Kafka.DEFAULT_DLQ_TOPIC);
    }

    public String getKafkaConsumerGroup() {
        return config.get("kafka.consumer.group", Kafka.DEFAULT_CONSUMER_GROUP);
    }

    public int getKafkaPollTimeoutMs() {
        return config.getInt("kafka.poll.timeout.ms", Kafka.DEFAULT_POLL_TIMEOUT_MS);
    }

    public int getMaxRetries() {
        return config.getInt("kafka.max.retries", Kafka.DEFAULT_MAX_RETRIES);
    }

    // ======== Metrics Configuration ========

    public static final class Metrics {
        public static final int DEFAULT_PROMETHEUS_PORT_WAREHOUSE = 9090;
        public static final int DEFAULT_PROMETHEUS_PORT_MONITORING = 9091;
    }

    public int getPrometheusPortWarehouse() {
        return config.getInt("metrics.prometheus.port.warehouse", Metrics.DEFAULT_PROMETHEUS_PORT_WAREHOUSE);
    }

    public int getPrometheusPortMonitoring() {
        return config.getInt("metrics.prometheus.port.monitoring", Metrics.DEFAULT_PROMETHEUS_PORT_MONITORING);
    }

    // ======== Health Check Configuration ========

    public static final class Health {
        public static final int DEFAULT_HTTP_PORT_WAREHOUSE = 8080;
        public static final int DEFAULT_HTTP_PORT_MONITORING = 8081;
    }

    public int getHealthPortWarehouse() {
        return config.getInt("health.http.port.warehouse", Health.DEFAULT_HTTP_PORT_WAREHOUSE);
    }

    public int getHealthPortMonitoring() {
        return config.getInt("health.http.port.monitoring", Health.DEFAULT_HTTP_PORT_MONITORING);
    }

    /**
     * Validate all configuration values.
     * Throws IllegalStateException if any configuration is invalid.
     * Should be called during application startup.
     */
    public void validate() {
        // Validate UDP ports
        validatePort("UDP Temperature Port", getTemperaturePort());
        validatePort("UDP Humidity Port", getHumidityPort());

        // Validate buffer size
        int bufferSize = getUdpBufferSize();
        if (bufferSize < 64 || bufferSize > 65536) {
            throw new IllegalStateException(
                "UDP buffer size must be between 64 and 65536, got: " + bufferSize);
        }

        // Validate rate limit
        int rateLimit = getUdpRateLimit();
        if (rateLimit < 1 || rateLimit > 1000000) {
            throw new IllegalStateException(
                "UDP rate limit must be between 1 and 1000000, got: " + rateLimit);
        }

        // Validate Kafka configuration
        String bootstrapServers = getKafkaBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            throw new IllegalStateException("Kafka bootstrap servers cannot be empty");
        }

        // Validate Kafka topics
        validateNonEmpty("Kafka main topic", getKafkaMainTopic());
        validateNonEmpty("Kafka retry topic 1", getKafkaRetryTopic1());
        validateNonEmpty("Kafka retry topic 2", getKafkaRetryTopic2());
        validateNonEmpty("Kafka DLQ topic", getKafkaDlqTopic());
        validateNonEmpty("Kafka consumer group", getKafkaConsumerGroup());

        // Validate poll timeout
        int pollTimeout = getKafkaPollTimeoutMs();
        if (pollTimeout < 1 || pollTimeout > 30000) {
            throw new IllegalStateException(
                "Kafka poll timeout must be between 1 and 30000ms, got: " + pollTimeout);
        }

        // Validate max retries
        int maxRetries = getMaxRetries();
        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalStateException(
                "Kafka max retries must be between 0 and 10, got: " + maxRetries);
        }

        // Validate Prometheus ports
        validatePort("Prometheus Port (Warehouse)", getPrometheusPortWarehouse());
        validatePort("Prometheus Port (Monitoring)", getPrometheusPortMonitoring());

        // Validate health check ports
        validatePort("Health Port (Warehouse)", getHealthPortWarehouse());
        validatePort("Health Port (Monitoring)", getHealthPortMonitoring());
    }

    private void validatePort(String name, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(
                name + " must be between 1 and 65535, got: " + port);
        }
    }

    private void validateNonEmpty(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " cannot be empty");
        }
    }
}
