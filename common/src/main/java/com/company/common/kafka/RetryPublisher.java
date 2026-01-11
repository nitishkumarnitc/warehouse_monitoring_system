package com.company.common.kafka;

import com.company.common.config.ServiceConfig;
import com.company.common.model.KafkaHeaders;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Publishes failed messages to retry topics or dead letter queue.
 * Non-static to enable proper lifecycle management and testing.
 */
public class RetryPublisher {

    private static final Logger logger = LoggerFactory.getLogger(RetryPublisher.class);

    private final Producer<String, String> producer;
    private final ServiceConfig config;

    public RetryPublisher(ServiceConfig config) {
        this.config = config;
        this.producer = KafkaProducerFactory.create(config);
        logger.info("RetryPublisher initialized with bootstrap servers: {}",
                    config.getKafkaBootstrapServers());
    }

    public void publish(String payload, int retryCount) {
        String topic = determineRetryTopic(retryCount);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        record.headers().add(new RecordHeader(
                KafkaHeaders.RETRY_COUNT,
                String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8)
        ));

        try {
            producer.send(record);
            producer.flush();
            logger.debug("Published message to {} with retry count {}", topic, retryCount);
        } catch (Exception e) {
            logger.error("Failed to publish to retry topic: {}", topic, e);
            throw new RuntimeException("Failed to publish retry message", e);
        }
    }

    private String determineRetryTopic(int retryCount) {
        if (retryCount == 1) {
            return config.getKafkaRetryTopic1();
        } else if (retryCount == 2) {
            return config.getKafkaRetryTopic2();
        } else {
            return config.getKafkaDlqTopic();
        }
    }

    /**
     * Close the producer and release resources.
     * Should be called during application shutdown.
     */
    public void close() {
        logger.info("Closing RetryPublisher...");
        try {
            producer.flush();
            producer.close();
            logger.info("RetryPublisher closed successfully");
        } catch (Exception e) {
            logger.error("Error closing RetryPublisher", e);
        }
    }
}
