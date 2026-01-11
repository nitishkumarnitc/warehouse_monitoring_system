package com.company.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceConfigTest {

    private ServiceConfig config;

    @BeforeEach
    void setUp() {
        config = new ServiceConfig(new ApplicationConfig());
    }

    @Test
    void shouldValidateDefaultConfiguration() {
        // Default configuration should be valid
        config.validate();
        // If no exception, validation passed
    }

    @Test
    void shouldGetDefaultValues() {
        assertThat(config.getTemperaturePort()).isEqualTo(3344);
        assertThat(config.getHumidityPort()).isEqualTo(3355);
        assertThat(config.getKafkaBootstrapServers()).isEqualTo("localhost:9092");
        assertThat(config.getKafkaMainTopic()).isEqualTo("sensor-events");
    }

    @Test
    void shouldGetKafkaTopics() {
        assertThat(config.getKafkaMainTopic()).isEqualTo("sensor-events");
        assertThat(config.getKafkaRetryTopic1()).isEqualTo("sensor-events-retry-1");
        assertThat(config.getKafkaRetryTopic2()).isEqualTo("sensor-events-retry-2");
        assertThat(config.getKafkaDlqTopic()).isEqualTo("sensor-events-dlq");
    }

    @Test
    void shouldGetPrometheusAndHealthPorts() {
        assertThat(config.getPrometheusPortWarehouse()).isEqualTo(9090);
        assertThat(config.getPrometheusPortMonitoring()).isEqualTo(9091);
        assertThat(config.getHealthPortWarehouse()).isEqualTo(8080);
        assertThat(config.getHealthPortMonitoring()).isEqualTo(8081);
    }

    @Test
    void shouldGetUdpConfiguration() {
        assertThat(config.getUdpBufferSize()).isEqualTo(1024);
        assertThat(config.getUdpRateLimit()).isEqualTo(1000);
    }

    @Test
    void shouldGetKafkaConsumerConfig() {
        assertThat(config.getKafkaConsumerGroup()).isEqualTo("central-monitoring-group");
        assertThat(config.getKafkaPollTimeoutMs()).isEqualTo(500);
        assertThat(config.getMaxRetries()).isEqualTo(3);
    }
}
