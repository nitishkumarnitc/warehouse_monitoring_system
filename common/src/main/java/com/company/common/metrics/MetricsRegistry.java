package com.company.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MetricsRegistry.class);
    private static PrometheusMeterRegistry registry;
    private static HTTPServer server;

    // Initialize Prometheus registry
    public static synchronized PrometheusMeterRegistry getRegistry() {
        if (registry == null) {
            registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            logger.info("Prometheus metrics registry initialized");
        }
        return registry;
    }

    // Start HTTP server to expose metrics
    public static synchronized void startMetricsServer(int port) {
        if (server != null) {
            logger.warn("Metrics server already running on port {}", server.getPort());
            return;
        }

        try {
            server = new HTTPServer.Builder()
                    .withPort(port)
                    .withRegistry(getRegistry().getPrometheusRegistry())
                    .build();
            logger.info("Prometheus metrics server started on port {}", port);
        } catch (IOException e) {
            logger.error("Failed to start metrics server on port {}", port, e);
            throw new RuntimeException("Failed to start metrics server", e);
        }
    }

    // Stop metrics server
    public static synchronized void stopMetricsServer() {
        if (server != null) {
            server.close();
            server = null;
            logger.info("Metrics server stopped");
        }
    }

    // Helper methods to create metrics
    public static Counter counter(String name, String description, String... tags) {
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(getRegistry());
    }

    public static Timer timer(String name, String description, String... tags) {
        return Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(getRegistry());
    }

    public static <T> Gauge gauge(String name, String description, T obj,
                                   java.util.function.ToDoubleFunction<T> valueFunction,
                                   String... tags) {
        return Gauge.builder(name, obj, valueFunction)
                .description(description)
                .tags(tags)
                .register(getRegistry());
    }

    public static AtomicLong atomicGauge(String name, String description, String... tags) {
        AtomicLong value = new AtomicLong(0);
        Gauge.builder(name, value, AtomicLong::get)
                .description(description)
                .tags(tags)
                .register(getRegistry());
        return value;
    }

    // For testing purposes - reset the singleton state
    public static synchronized void resetForTesting() {
        if (server != null) {
            server.close();
            server = null;
        }
        if (registry != null) {
            registry.close();
            registry = null;
        }
        logger.debug("MetricsRegistry reset for testing");
    }
}
