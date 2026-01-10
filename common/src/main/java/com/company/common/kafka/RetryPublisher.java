package com.company.common.kafka;

import com.company.common.model.KafkaHeaders;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;

public class RetryPublisher {

    private static final Producer<String, String> producer =
            KafkaProducerFactory.create();

    public static void publish(
            String payload,
            int retryCount
    ) {
        String topic;

        if (retryCount == 1) {
            topic = "sensor-events-retry-1";
        } else if (retryCount == 2) {
            topic = "sensor-events-retry-2";
        } else {
            topic = "sensor-events-dlq";
        }

        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, payload);

        record.headers().add(new RecordHeader(
                KafkaHeaders.RETRY_COUNT,
                String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8)
        ));

        producer.send(record);
        producer.flush();
    }
}
