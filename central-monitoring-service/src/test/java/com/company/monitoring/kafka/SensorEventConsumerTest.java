package com.company.monitoring.kafka;

import com.company.common.model.KafkaHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensorEventConsumerTest {

    // Note: Testing getRetryCount method which is package-private
    // Cannot easily mock Kafka Consumer due to Java module system constraints

    @Test
    void shouldGetRetryCountFromHeader() {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sensor-events-retry-1", 0, 0L, null, "{}"
        );
        record.headers().add(new RecordHeader(
                KafkaHeaders.RETRY_COUNT,
                "2".getBytes(StandardCharsets.UTF_8)
        ));

        int retryCount = consumer.getRetryCount(record);

        assertThat(retryCount).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroWhenNoRetryHeader() {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sensor-events", 0, 0L, null, "{}"
        );

        int retryCount = consumer.getRetryCount(record);

        assertThat(retryCount).isEqualTo(0);
    }

    @Test
    void shouldHandleRetryHeaderWithDifferentValues() {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        ConsumerRecord<String, String> record1 = new ConsumerRecord<>(
                "sensor-events-retry-1", 0, 0L, null, "{}"
        );
        record1.headers().add(new RecordHeader(
                KafkaHeaders.RETRY_COUNT,
                "1".getBytes(StandardCharsets.UTF_8)
        ));

        ConsumerRecord<String, String> record2 = new ConsumerRecord<>(
                "sensor-events-retry-2", 0, 0L, null, "{}"
        );
        record2.headers().add(new RecordHeader(
                KafkaHeaders.RETRY_COUNT,
                "3".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(consumer.getRetryCount(record1)).isEqualTo(1);
        assertThat(consumer.getRetryCount(record2)).isEqualTo(3);
    }

    @Test
    void shouldProcessValidSensorEvent() throws Exception {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        String json = "{\"reading\":{" +
                "\"sensorId\":\"sensor-123\"," +
                "\"value\":25.5," +
                "\"sensorType\":\"TEMPERATURE\"," +
                "\"unit\":\"CELSIUS\"," +
                "\"timestamp\":\"2024-01-01T10:00:00Z\"" +
                "}}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sensor-events", 0, 0L, null, json
        );

        consumer.process(record);
        // If no exception, the process was successful
    }

    @Test
    void shouldRejectNegativeValues() {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        String json = "{\"reading\":{" +
                "\"sensorId\":\"sensor-123\"," +
                "\"value\":-10.0," +
                "\"sensorType\":\"TEMPERATURE\"," +
                "\"unit\":\"CELSIUS\"," +
                "\"timestamp\":\"2024-01-01T10:00:00Z\"" +
                "}}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sensor-events", 0, 0L, null, json
        );

        assertThatThrownBy(() -> consumer.process(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid sensor value: -10.0");
    }

    @Test
    void shouldAcceptZeroValue() throws Exception {
        TestSensorEventConsumer consumer = new TestSensorEventConsumer("sensor-events");

        String json = "{\"reading\":{" +
                "\"sensorId\":\"sensor-123\"," +
                "\"value\":0.0," +
                "\"sensorType\":\"TEMPERATURE\"," +
                "\"unit\":\"CELSIUS\"," +
                "\"timestamp\":\"2024-01-01T10:00:00Z\"" +
                "}}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sensor-events", 0, 0L, null, json
        );

        consumer.process(record);
        // Should not throw exception
    }

    // Test-specific subclass that doesn't connect to real Kafka
    private static class TestSensorEventConsumer extends SensorEventConsumer {
        public TestSensorEventConsumer(String topic) {
            super(topic, null, null); // null consumer and retry publisher for testing
        }
    }
}
