package com.company.monitoring.kafka;

import com.company.common.kafka.*;
import com.company.common.event.SensorEvent;
import com.company.common.model.KafkaHeaders;
import com.company.monitoring.kafka.KafkaConsumerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;

import java.time.Duration;
import java.util.List;

public class SensorEventConsumer implements Runnable {

    private final Consumer<String, String> consumer;
    private final ObjectMapper mapper = new ObjectMapper();

    public SensorEventConsumer(String topic) {
        this.consumer = KafkaConsumerFactory.create();
        consumer.subscribe(List.of(topic));
    }

    @Override
    public void run() {
        while (true) {
            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    process(record);
                } catch (Exception e) {
                    handleFailure(record);
                }
            }
        }
    }

    private void process(ConsumerRecord<String, String> record) throws Exception {
        SensorEvent event =
                mapper.readValue(record.value(), SensorEvent.class);

        // Simulate failure
        if (event.getReading().getValue() < 0) {
            throw new RuntimeException("Invalid sensor value");
        }

        System.out.println("✅ Processed: " + event.getReading());
    }

    private void handleFailure(ConsumerRecord<String, String> record) {
        int retryCount = getRetryCount(record) + 1;

        System.out.println("🔁 Retry attempt: " + retryCount);

        RetryPublisher.publish(record.value(), retryCount);
    }

    private int getRetryCount(ConsumerRecord<String, String> record) {
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
