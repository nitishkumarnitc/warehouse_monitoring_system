package com.company.warehouse.udp;

import com.company.common.event.SensorEvent;
import com.company.common.model.SensorReading;
import com.company.common.model.SensorType;
import com.company.common.model.MeasurementUnit;
import com.company.common.metrics.MetricsRegistry;
import com.company.warehouse.kafka.KafkaProducerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class UdpSensorListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(UdpSensorListener.class);

    private final int port;
    private final SensorType sensorType;
    private final Producer<String, String> producer;
    private final ObjectMapper mapper;

    // Metrics
    private final Counter packetsReceived;
    private final Counter packetsProcessed;
    private final Counter packetsError;
    private final Timer processingTime;

    {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    public UdpSensorListener(int port, SensorType sensorType) {
        this(port, sensorType, KafkaProducerFactory.create());
    }

    // Constructor with producer injection for testing
    public UdpSensorListener(int port, SensorType sensorType, Producer<String, String> producer) {
        this.port = port;
        this.sensorType = sensorType;
        this.producer = producer;

        // Initialize metrics
        String portTag = String.valueOf(port);
        String typeTag = sensorType.name();

        this.packetsReceived = MetricsRegistry.counter(
                "udp_packets_received_total",
                "Total number of UDP packets received",
                "port", portTag, "type", typeTag
        );

        this.packetsProcessed = MetricsRegistry.counter(
                "udp_packets_processed_total",
                "Total number of UDP packets successfully processed",
                "port", portTag, "type", typeTag
        );

        this.packetsError = MetricsRegistry.counter(
                "udp_packets_error_total",
                "Total number of UDP packets that failed processing",
                "port", portTag, "type", typeTag
        );

        this.processingTime = MetricsRegistry.timer(
                "udp_packet_processing_duration_seconds",
                "Time taken to process UDP packet",
                "port", portTag, "type", typeTag
        );
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];

            logger.info("UDP listener started on port {} for {}", port, sensorType);

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    packetsReceived.increment();

                    processingTime.record(() -> processPacket(packet));

                } catch (Exception e) {
                    packetsError.increment();
                    logger.error("Error processing UDP packet on port {}", port, e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to start UDP listener on port {}", port, e);
            throw new RuntimeException("UDP listener failed", e);
        }
    }

    // Package-private for testing
    void processPacket(DatagramPacket packet) {
        try {
            String payload = new String(
                    packet.getData(),
                    0,
                    packet.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

            double value = Double.parseDouble(payload);

            SensorReading reading = new SensorReading(
                    "sensor-" + port,
                    value,
                    sensorType,
                    MeasurementUnit.CELSIUS,
                    Instant.now()
            );

            SensorEvent event = new SensorEvent(reading);
            String json = mapper.writeValueAsString(event);

            producer.send(new ProducerRecord<>("sensor-events", json));
            packetsProcessed.increment();

            logger.debug("Processed sensor reading: sensorId={}, value={}, type={}",
                    reading.getSensorId(), value, sensorType);

        } catch (NumberFormatException e) {
            logger.warn("Invalid numeric value received on port {}: {}", port, e.getMessage());
            throw new RuntimeException("Invalid sensor value", e);
        } catch (Exception e) {
            logger.error("Failed to process sensor reading on port {}", port, e);
            throw new RuntimeException("Processing failed", e);
        }
    }
}
