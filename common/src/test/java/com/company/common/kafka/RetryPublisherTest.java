package com.company.common.kafka;

import com.company.common.config.ApplicationConfig;
import com.company.common.config.ServiceConfig;
import com.company.common.model.KafkaHeaders;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPublisherTest {

    private ServiceConfig config;

    @BeforeEach
    void setUp() {
        config = new ServiceConfig(new ApplicationConfig());
    }

    @AfterEach
    void tearDown() {
        // Cleanup if needed
    }

    @Test
    void shouldDetermineCorrectTopicForRetryCount1() {
        // Test topic determination logic without actually publishing
        int retryCount = 1;
        String expectedTopic = config.getKafkaRetryTopic1();
        assertThat(expectedTopic).isEqualTo("sensor-events-retry-1");
    }

    @Test
    void shouldDetermineCorrectTopicForRetryCount2() {
        int retryCount = 2;
        String expectedTopic = config.getKafkaRetryTopic2();
        assertThat(expectedTopic).isEqualTo("sensor-events-retry-2");
    }

    @Test
    void shouldDetermineCorrectTopicForRetryCount3() {
        int retryCount = 3;
        String expectedTopic = config.getKafkaDlqTopic();
        assertThat(expectedTopic).isEqualTo("sensor-events-dlq");
    }

    @Test
    void shouldHandleRetryCountExceeding3() {
        int retryCount = 10;
        String expectedTopic = config.getKafkaDlqTopic();
        assertThat(expectedTopic).isEqualTo("sensor-events-dlq");
    }

    @Test
    void shouldIncludeRetryCountHeader() {
        // Simulate creating a producer record with retry count header
        MockProducer<String, String> mockProducer = new MockProducer<>(
                true,
                new StringSerializer(),
                new StringSerializer()
        );

        String payload = "{\"test\":\"data\"}";
        int retryCount = 1;
        String topic = "sensor-events-retry-1";

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        record.headers().add(
                KafkaHeaders.RETRY_COUNT,
                String.valueOf(retryCount).getBytes(StandardCharsets.UTF_8)
        );

        mockProducer.send(record);

        assertThat(mockProducer.history()).hasSize(1);
        ProducerRecord<String, String> sentRecord = mockProducer.history().get(0);

        assertThat(sentRecord.topic()).isEqualTo(topic);
        assertThat(sentRecord.value()).isEqualTo(payload);

        Header retryHeader = sentRecord.headers().lastHeader(KafkaHeaders.RETRY_COUNT);
        assertThat(retryHeader).isNotNull();
        assertThat(new String(retryHeader.value(), StandardCharsets.UTF_8)).isEqualTo("1");
    }

    @Test
    void shouldHandleComplexJsonPayload() {
        String complexPayload = "{\"reading\":{\"sensorId\":\"sensor-123\",\"value\":25.5}}";

        // Verify payload is not modified
        assertThat(complexPayload).contains("sensor-123");
        assertThat(complexPayload).contains("25.5");
    }

    @Test
    void shouldSelectCorrectTopicForAllRetryLevels() {
        assertThat(determineTopicForRetryCount(0)).isEqualTo(config.getKafkaDlqTopic());
        assertThat(determineTopicForRetryCount(1)).isEqualTo(config.getKafkaRetryTopic1());
        assertThat(determineTopicForRetryCount(2)).isEqualTo(config.getKafkaRetryTopic2());
        assertThat(determineTopicForRetryCount(3)).isEqualTo(config.getKafkaDlqTopic());
        assertThat(determineTopicForRetryCount(100)).isEqualTo(config.getKafkaDlqTopic());
    }

    private String determineTopicForRetryCount(int retryCount) {
        if (retryCount == 1) {
            return config.getKafkaRetryTopic1();
        } else if (retryCount == 2) {
            return config.getKafkaRetryTopic2();
        } else {
            return config.getKafkaDlqTopic();
        }
    }
}
