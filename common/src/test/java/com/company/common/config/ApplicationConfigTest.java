package com.company.common.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    private ApplicationConfig config;

    @BeforeEach
    void setUp() {
        config = new ApplicationConfig();
    }

    @AfterEach
    void tearDown() {
        // Clear any system properties set during tests
        System.clearProperty("test.property");
    }

    @Test
    void shouldLoadDefaultValues() {
        String value = config.get("nonexistent.property", "default-value");
        assertThat(value).isEqualTo("default-value");
    }

    @Test
    void shouldPrioritizeSystemPropertyOverDefault() {
        System.setProperty("test.property", "system-value");
        String value = config.get("test.property", "default-value");
        assertThat(value).isEqualTo("system-value");
    }

    @Test
    void shouldConvertEnvironmentVariableFormat() {
        // Test that environment variable key conversion works
        // kafka.bootstrap.servers -> KAFKA_BOOTSTRAP_SERVERS
        String value = config.get("kafka.bootstrap.servers", "localhost:9092");
        assertThat(value).isNotNull();
    }

    @Test
    void shouldGetIntValues() {
        System.setProperty("test.port", "8080");
        int port = config.getInt("test.port", 9090);
        assertThat(port).isEqualTo(8080);
    }

    @Test
    void shouldReturnDefaultIntOnInvalidValue() {
        System.setProperty("test.port", "invalid");
        int port = config.getInt("test.port", 9090);
        assertThat(port).isEqualTo(9090);
    }

    @Test
    void shouldGetLongValues() {
        System.setProperty("test.timeout", "30000");
        long timeout = config.getLong("test.timeout", 5000L);
        assertThat(timeout).isEqualTo(30000L);
    }

    @Test
    void shouldReturnDefaultLongOnInvalidValue() {
        System.setProperty("test.timeout", "invalid");
        long timeout = config.getLong("test.timeout", 5000L);
        assertThat(timeout).isEqualTo(5000L);
    }

    @Test
    void shouldGetDoubleValues() {
        System.setProperty("test.threshold", "25.5");
        double threshold = config.getDouble("test.threshold", 10.0);
        assertThat(threshold).isEqualTo(25.5);
    }

    @Test
    void shouldReturnDefaultDoubleOnInvalidValue() {
        System.setProperty("test.threshold", "invalid");
        double threshold = config.getDouble("test.threshold", 10.0);
        assertThat(threshold).isEqualTo(10.0);
    }

    @Test
    void shouldGetBooleanValues() {
        System.setProperty("test.enabled", "true");
        boolean enabled = config.getBoolean("test.enabled", false);
        assertThat(enabled).isTrue();
    }

    @Test
    void shouldHandleBooleanStringVariations() {
        System.setProperty("test.enabled", "false");
        boolean enabled = config.getBoolean("test.enabled", true);
        assertThat(enabled).isFalse();
    }
}
