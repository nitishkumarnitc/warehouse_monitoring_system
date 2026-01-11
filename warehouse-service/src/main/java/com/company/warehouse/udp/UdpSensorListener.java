package com.company.warehouse.udp;

import com.company.common.config.ServiceConfig;
import com.company.common.event.SensorEvent;
import com.company.common.model.MeasurementUnit;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import com.company.common.metrics.MetricsRegistry;
import com.company.common.resilience.ResilientKafkaProducer;
import com.company.common.resilience.UdpRateLimiter;
import com.company.common.validation.SensorValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * UDP listener for warehouse sensor data.
 * Receives sensor readings via UDP and publishes to Kafka.
 *
 * Features:
 * - Rate limiting to prevent system overwhelm
 * - Circuit breaker for Kafka failures
 * - Input validation (NaN, Infinity, range checks)
 * - Structured logging with MDC
 * - Metrics tracking
 */
public class UdpSensorListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UdpSensorListener.class);

    private final int port;
    private final SensorType sensorType;
    private final ResilientKafkaProducer<String, String> producer;
    private final ObjectMapper mapper;
    private final UdpRateLimiter rateLimiter;
    private final String topicName;
    private final int bufferSize;

    // Metrics
    private final Counter packetsReceived;
    private final Counter packetsProcessed;
    private final Counter packetsDropped;
    private final Counter packetsError;
    private final Counter validationErrors;
    private final Timer processingTime;

    private volatile boolean running = true;
    private DatagramSocket socket;

    // Test-friendly constructor - accepts Producer for testing with MockProducer
    UdpSensorListener(int port, SensorType sensorType,
                      org.apache.kafka.clients.producer.Producer<String, String> testProducer) {
        this.port = port;
        this.sensorType = sensorType;
        this.topicName = "sensor-events";
        this.bufferSize = 1024;

        // For tests, wrap the mock producer directly without circuit breaker
        // This is a workaround since MockProducer can't be cast to KafkaProducer
        this.producer = new ResilientKafkaProducer<String, String>(null, "test") {
            @Override
            public java.util.concurrent.CompletableFuture<org.apache.kafka.clients.producer.RecordMetadata>
                sendAsync(org.apache.kafka.clients.producer.ProducerRecord<String, String> record) {
                testProducer.send(record);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };

        this.rateLimiter = new UdpRateLimiter("test-" + port, 10000, java.time.Duration.ofSeconds(1));
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());

        // Initialize metrics
        String portTag = String.valueOf(port);
        String typeTag = sensorType.name();
        this.packetsReceived = MetricsRegistry.counter("udp_packets_received_total", "Test", "port", portTag, "type", typeTag);
        this.packetsProcessed = MetricsRegistry.counter("udp_packets_processed_total", "Test", "port", portTag, "type", typeTag);
        this.packetsDropped = MetricsRegistry.counter("udp_packets_dropped_total", "Test", "port", portTag, "type", typeTag);
        this.packetsError = MetricsRegistry.counter("udp_packets_error_total", "Test", "port", portTag, "type", typeTag);
        this.validationErrors = MetricsRegistry.counter("udp_validation_errors_total", "Test", "port", portTag, "type", typeTag);
        this.processingTime = MetricsRegistry.timer("udp_packet_processing_duration_seconds", "Test", "port", portTag, "type", typeTag);
    }

    public UdpSensorListener(int port, SensorType sensorType,
                            ResilientKafkaProducer<String, String> producer,
                            ServiceConfig config) {
        this.port = port;
        this.sensorType = sensorType;
        this.producer = producer;
        this.topicName = config.getKafkaMainTopic();
        this.bufferSize = config.getUdpBufferSize();

        // Initialize rate limiter
        this.rateLimiter = new UdpRateLimiter(
            "udp-listener-" + port,
            config.getUdpRateLimit(),
            java.time.Duration.ofSeconds(1)
        );

        // Initialize JSON mapper
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());

        // Initialize metrics
        String portTag = String.valueOf(port);
        String typeTag = sensorType.name();

        this.packetsReceived = MetricsRegistry.counter(
                "udp_packets_received_total",
                "Total UDP packets received",
                "port", portTag, "type", typeTag
        );

        this.packetsProcessed = MetricsRegistry.counter(
                "udp_packets_processed_total",
                "Total UDP packets successfully processed",
                "port", portTag, "type", typeTag
        );

        this.packetsDropped = MetricsRegistry.counter(
                "udp_packets_dropped_total",
                "Total UDP packets dropped due to rate limiting",
                "port", portTag, "type", typeTag
        );

        this.packetsError = MetricsRegistry.counter(
                "udp_packets_error_total",
                "Total UDP packets that failed processing",
                "port", portTag, "type", typeTag
        );

        this.validationErrors = MetricsRegistry.counter(
                "udp_validation_errors_total",
                "Total validation errors",
                "port", portTag, "type", typeTag
        );

        this.processingTime = MetricsRegistry.timer(
                "udp_packet_processing_duration_seconds",
                "UDP packet processing duration",
                "port", portTag, "type", typeTag
        );
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            logger.info("UDP listener started on port {} for {} (buffer={}B, rateLimit={}/s)",
                port, sensorType, bufferSize, rateLimiter.getMetrics().getAvailablePermissions());

            byte[] buffer = new byte[bufferSize];

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    packetsReceived.increment();

                    // Apply rate limiting
                    if (!rateLimiter.tryAcquire()) {
                        packetsDropped.increment();
                        logger.warn("Packet dropped due to rate limiting on port {}", port);
                        continue;
                    }

                    // Process with timing
                    processingTime.record(() -> processPacket(packet));

                } catch (SocketException e) {
                    if (running) {
                        packetsError.increment();
                        logger.error("Socket error on port {}", port, e);
                    }
                    // If socket closed, exit gracefully
                    break;
                } catch (Exception e) {
                    packetsError.increment();
                    logger.error("Error processing UDP packet on port {}", port, e);
                }
            }

            logger.info("UDP listener stopped on port {}", port);

        } catch (SocketException e) {
            logger.error("Failed to create UDP socket on port {}", port, e);
            throw new RuntimeException("UDP listener failed to start", e);
        } finally {
            closeSocket();
        }
    }

    void processPacket(DatagramPacket packet) {
        String sensorId = "sensor-" + port;

        try {
            // Set MDC for structured logging
            MDC.put("sensorId", sensorId);
            MDC.put("port", String.valueOf(port));
            MDC.put("sensorType", sensorType.name());

            // Parse payload
            String payload = new String(
                    packet.getData(),
                    0,
                    packet.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

            logger.trace("Received payload: {}", payload);

            // Parse value
            double value;
            try {
                value = Double.parseDouble(payload);
            } catch (NumberFormatException e) {
                logger.warn("Invalid numeric value received: '{}' - {}", payload, e.getMessage());
                throw new IllegalArgumentException("Invalid sensor value: " + payload, e);
            }

            // Create sensor reading
            MeasurementUnit unit = (sensorType == SensorType.TEMPERATURE)
                ? MeasurementUnit.CELSIUS
                : MeasurementUnit.PERCENT;

            SensorReading reading = new SensorReading(
                    sensorId,
                    value,
                    sensorType,
                    unit,
                    Instant.now()
            );

            // Validate reading
            SensorValidator.ValidationResult validation = SensorValidator.validate(reading);
            if (!validation.isValid()) {
                validationErrors.increment();
                logger.warn("Validation failed: {}", validation.getErrorMessage());
                throw new IllegalArgumentException(validation.getErrorMessage());
            }

            // Create event and serialize
            SensorEvent event = new SensorEvent(reading);
            String json = mapper.writeValueAsString(event);

            // Publish to Kafka with circuit breaker
            ProducerRecord<String, String> record = new ProducerRecord<>(topicName, sensorId, json);

            try {
                producer.sendAsync(record)
                    .whenComplete((metadata, exception) -> {
                        if (exception != null) {
                            logger.error("Failed to send sensor reading to Kafka: sensorId={}, value={}",
                                sensorId, value, exception);
                        } else {
                            packetsProcessed.increment();
                            logger.debug("Published sensor reading: sensorId={}, value={}, partition={}, offset={}",
                                sensorId, value, metadata.partition(), metadata.offset());
                        }
                    });
            } catch (IllegalStateException e) {
                // Circuit breaker is open
                logger.error("Circuit breaker OPEN - Kafka unavailable, dropping packet: sensorId={}",
                    sensorId);
                throw e;
            }

        } catch (IllegalArgumentException e) {
            // Validation or parse error - already logged
            throw new RuntimeException("Invalid sensor data", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing sensor reading", e);
            throw new RuntimeException("Processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Stop the listener gracefully.
     */
    public void stop() {
        logger.info("Stopping UDP listener on port {}...", port);
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.debug("UDP socket closed on port {}", port);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public SensorType getSensorType() {
        return sensorType;
    }
}
