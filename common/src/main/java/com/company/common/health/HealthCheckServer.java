package com.company.common.health;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server for health checks and readiness probes.
 * Exposes /health and /ready endpoints.
 */
public class HealthCheckServer {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServer.class);

    private final HttpServer server;
    private final Map<String, HealthIndicator> healthIndicators = new ConcurrentHashMap<>();
    private volatile boolean ready = false;

    public interface HealthIndicator {
        boolean isHealthy();
        String getStatus();
    }

    public HealthCheckServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(2));

        // Health endpoint - checks if service components are healthy
        server.createContext("/health", exchange -> {
            boolean healthy = checkHealth();
            int statusCode = healthy ? 200 : 503;
            String response = buildHealthResponse(healthy);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.length());

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        // Ready endpoint - checks if service is ready to accept traffic
        server.createContext("/ready", exchange -> {
            int statusCode = ready ? 200 : 503;
            String response = String.format("{\"ready\":%b}", ready);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.length());

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        logger.info("Health check server created on port {}", port);
    }

    public void start() {
        server.start();
        logger.info("Health check server started");
    }

    public void stop() {
        server.stop(0);
        logger.info("Health check server stopped");
    }

    public void registerHealthIndicator(String name, HealthIndicator indicator) {
        healthIndicators.put(name, indicator);
        logger.debug("Registered health indicator: {}", name);
    }

    public void setReady(boolean ready) {
        this.ready = ready;
        logger.info("Service readiness set to: {}", ready);
    }

    private boolean checkHealth() {
        if (healthIndicators.isEmpty()) {
            return true; // No indicators means healthy by default
        }

        return healthIndicators.values().stream()
                .allMatch(HealthIndicator::isHealthy);
    }

    private String buildHealthResponse(boolean healthy) {
        StringBuilder json = new StringBuilder();
        json.append("{\"status\":\"").append(healthy ? "UP" : "DOWN").append("\"");

        if (!healthIndicators.isEmpty()) {
            json.append(",\"checks\":{");
            boolean first = true;
            for (Map.Entry<String, HealthIndicator> entry : healthIndicators.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                json.append("{\"healthy\":").append(entry.getValue().isHealthy());
                json.append(",\"status\":\"").append(entry.getValue().getStatus()).append("\"}");
                first = false;
            }
            json.append("}");
        }

        json.append("}");
        return json.toString();
    }
}
