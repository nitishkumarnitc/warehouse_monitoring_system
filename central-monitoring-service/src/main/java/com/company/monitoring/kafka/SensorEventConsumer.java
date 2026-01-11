package com.company.monitoring.kafka;

import com.company.common.kafka.*;
import com.company.common.event.SensorEvent;
import com.company.common.model.KafkaHeaders;
import com.company.common.metrics.MetricsRegistry;
import com.company.monitoring.kafka.KafkaConsumerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

public class SensorEventConsumer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SensorEventConsumer.class);

    private final Consumer<String, String> consumer;
    private final ObjectMapper mapper;
    private final String topic;

    // Metrics
    private final Counter messagesReceived;
    private final Counter messagesProcessed;
    private final Counter messagesError;
    private final Counter retryAttempts;
    private final Timer processingTime;

    {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    public SensorEventConsumer(String topic) {
        this(topic, KafkaConsumerFactory.create());
    }

    // Constructor with consumer injection for testing
    public SensorEventConsumer(String topic, Consumer<String, String> consumer) {
        this.topic = topic;
        this.consumer = consumer;
        if (consumer != null) {
            consumer.subscribe(List.of(topic));
        }

        // Initialize metrics
        this.messagesReceived = MetricsRegistry.counter(
                "kafka_messages_received_total",
                "Total number of Kafka messages received",
                "topic", topic
        );

        this.messagesProcessed = MetricsRegistry.counter(
                "kafka_messages_processed_total",
                "Total number of Kafka messages successfully processed",
                "topic", topic
        );

        this.messagesError = MetricsRegistry.counter(
                "kafka_messages_error_total",
                "Total number of Kafka messages that failed processing",
                "topic", topic
        );

        this.retryAttempts = MetricsRegistry.counter(
                "kafka_retry_attempts_total",
                "Total number of retry attempts",
                "topic", topic
        );

        this.processingTime = MetricsRegistry.timer(
                "kafka_message_processing_duration_seconds",
                "Time taken to process Kafka message",
                "topic", topic
        );

        logger.info("Kafka consumer started for topic: {}", topic);
    }

    @Override
    public void run() {
        while (true) {
            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, String> record : records) {
                messagesReceived.increment();
                try {
                    processingTime.record(() -> {
                        try {
                            process(record);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    messagesError.increment();
                    logger.error("Error processing record from topic {}: {}",
                            topic, e.getMessage(), e);
                    handleFailure(record);
                }
            }
        }
    }

    // Package-private for testing
    void process(ConsumerRecord<String, String> record) throws Exception {
        SensorEvent event =
                mapper.readValue(record.value(), SensorEvent.class);

        // Simulate failure for validation
        if (event.getReading().getValue() < 0) {
            throw new RuntimeException("Invalid sensor value: " + event.getReading().getValue());
        }

        messagesProcessed.increment();

        logger.info("Processed sensor reading: sensorId={}, value={}, type={}",
                event.getReading().getSensorId(),
                event.getReading().getValue(),
                event.getReading().getSensorType());
    }

    private void handleFailure(ConsumerRecord<String, String> record) {
        int retryCount = getRetryCount(record) + 1;
        retryAttempts.increment();

        logger.warn("Retry attempt {} for message from topic: {}", retryCount, topic);

        RetryPublisher.publish(record.value(), retryCount);
    }

    // Package-private for testing
    int getRetryCount(ConsumerRecord<String, String> record) {
        if (record.headers().lastHeader(KafkaHeaders.RETRY_COUNT) == null) {
            return 0;
        }

        return Integer.parseInt(
                new String(
                        record.headers()
                                .lastHeader(KafkaHeaders.RETRY_COUNT)
                                .value()
                )
        );
    }
}
